package com.campuspilot.assistant;

import cn.hutool.core.util.StrUtil;
import com.campuspilot.assistant.dto.AssistantChatRequest;
import com.campuspilot.assistant.dto.AssistantChatResponse;
import com.campuspilot.assistant.dto.AssistantConversationMessageDTO;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.utils.UserHolder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 将浏览器请求安全转发到 Python 助手服务。 */
@Slf4j
@Service
public class AssistantServiceImpl implements IAssistantService {

    private static final String INTERNAL_TOKEN_HEADER = "X-Assistant-Internal-Token";
    private static final String CHAT_PATH = "/v1/assistant/chat";

    @Resource(name = "assistantRestTemplate")
    private RestTemplate restTemplate;

    @Resource
    private AssistantProperties properties;

    @Resource
    private AssistantUserContextService userContextService;

    @Resource
    private AssistantConversationService conversationService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MeterRegistry meterRegistry;

    /** 校验请求并转发用户上下文，不向浏览器暴露 Python 服务地址。 */
    @Override
    public Result chat(AssistantChatRequest request) {
        long started = System.nanoTime();
        String outcome = "error";
        try {
            if (!properties.isEnabled()) {
                outcome = "disabled";
                return Result.fail("智能助手暂未启用");
            }
            if (request == null || StrUtil.isBlank(request.getMessage())) {
                outcome = "bad_request";
                return Result.fail("请输入想咨询的内容");
            }
            String message = request.getMessage().trim();
            if (message.length() > 1000) {
                outcome = "bad_request";
                return Result.fail("单次消息不能超过1000个字符");
            }
            UserDTO user = UserHolder.getUser();
            if (user == null || user.getId() == null) {
                outcome = "unauthorized";
                return Result.fail("请先登录");
            }
            if (!allowRequest(user.getId())) {
                outcome = "rate_limited";
                return Result.fail("提问过于频繁，请稍后再试");
            }

            String threadId = resolveThreadId(request.getThreadId());
            String pagePath = StrUtil.sub(StrUtil.nullToEmpty(request.getPagePath()), 0, 256);
            RLock lock = redissonClient.getLock(
                    "lock:assistant:chat:" + user.getId() + ":" + threadId);
            boolean locked = false;
            try {
                locked = lock.tryLock(
                        500, properties.getReadTimeoutMillis() + 5000L, TimeUnit.MILLISECONDS);
                if (!locked) {
                    outcome = "thread_busy";
                    return Result.fail("上一条消息仍在处理中，请稍后重试");
                }

                List<AssistantConversationMessageDTO> history =
                        conversationService.load(user.getId(), threadId);
                String userContextToken = userContextService.issue(user);
                Map<String, Object> payload = new HashMap<>();
                payload.put("threadId", threadId);
                payload.put("message", message);
                payload.put("pagePath", pagePath);
                payload.put("userContextToken", userContextToken);
                payload.put("history", history);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set(INTERNAL_TOKEN_HEADER, properties.getInternalToken());

                AssistantChatResponse response = restTemplate.postForObject(
                        properties.getPythonBaseUrl() + CHAT_PATH,
                        new HttpEntity<>(payload, headers),
                        AssistantChatResponse.class);
                if (response == null || StrUtil.isBlank(response.getAnswer())) {
                    outcome = "empty_response";
                    return Result.fail("智能助手返回了空结果，请稍后再试");
                }
                response.setThreadId(threadId);
                conversationService.appendTurn(
                        user.getId(), threadId, message, response.getAnswer());
                outcome = "success";
                return Result.ok(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                outcome = "interrupted";
                return Result.fail("请求被中断，请重试");
            } catch (HttpStatusCodeException e) {
                log.warn("assistant service returned status={}", e.getRawStatusCode());
                if (e.getRawStatusCode() == 504) {
                    outcome = "timeout";
                    return Result.fail("智能助手处理超时，请缩小问题范围后重试");
                }
                if (e.getRawStatusCode() == 502) {
                    outcome = "tool_unavailable";
                    return Result.fail("校园业务数据暂时不可用，请稍后重试");
                }
                outcome = "upstream_error";
                return Result.fail("智能助手暂时不可用，请稍后再试");
            } catch (RestClientException e) {
                outcome = "upstream_unavailable";
                log.warn("assistant service unavailable: {}", e.getMessage());
                return Result.fail("智能助手暂时不可用，请稍后再试");
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } finally {
            meterRegistry.counter("assistant.chat.requests", "outcome", outcome).increment();
            meterRegistry.timer("assistant.chat.latency", "outcome", outcome)
                    .record(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    /** 限制单用户每分钟请求数，Redis 异常时保持服务可用。 */
    private boolean allowRequest(Long userId) {
        if (properties.getRateLimitPerMinute() <= 0) {
            return true;
        }
        long minute = System.currentTimeMillis() / 60000L;
        String key = "assistant:rate:" + userId + ":" + minute;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, 70, TimeUnit.SECONDS);
            }
            return count == null || count <= properties.getRateLimitPerMinute();
        } catch (RuntimeException e) {
            log.warn("assistant rate limiter unavailable; allowing request");
            return true;
        }
    }

    /** 为历史客户端补充会话标识。 */
    private String resolveThreadId(String value) {
        if (StrUtil.isBlank(value)) {
            return "web-" + System.currentTimeMillis();
        }
        return StrUtil.sub(value.trim(), 0, 128);
    }
}
