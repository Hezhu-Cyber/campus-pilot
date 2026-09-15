package com.campuspilot.assistant;

import cn.hutool.core.util.StrUtil;
import com.campuspilot.assistant.dto.AssistantConversationMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 使用 Redis 保存有界、可过期的会话历史，支持多实例部署。 */
@Slf4j
@Service
public class AssistantConversationService {

    private static final String HISTORY_KEY_PREFIX = "assistant:history:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AssistantProperties properties;

    /** 读取最近若干轮消息，损坏的历史项会被跳过。 */
    public List<AssistantConversationMessageDTO> load(Long userId, String threadId) {
        if (userId == null || StrUtil.isBlank(threadId)) {
            return Collections.emptyList();
        }
        String key = historyKey(userId, threadId);
        int maxMessages = properties.getHistoryMaxMessages();
        List<String> values = stringRedisTemplate.opsForList().range(key, -maxMessages, -1);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<AssistantConversationMessageDTO> messages = new ArrayList<>();
        for (String value : values) {
            try {
                messages.add(objectMapper.readValue(value, AssistantConversationMessageDTO.class));
            } catch (Exception e) {
                log.warn("skip corrupted assistant history entry");
            }
        }
        return messages;
    }

    /** 追加一轮问答并维持有界长度和 TTL。 */
    public void appendTurn(Long userId, String threadId, String userMessage, String assistantMessage) {
        if (userId == null || StrUtil.isBlank(threadId) || StrUtil.isBlank(assistantMessage)) {
            return;
        }
        String key = historyKey(userId, threadId);
        try {
            List<String> values = new ArrayList<>(2);
            values.add(objectMapper.writeValueAsString(
                    new AssistantConversationMessageDTO("user", userMessage)));
            values.add(objectMapper.writeValueAsString(
                    new AssistantConversationMessageDTO("assistant", assistantMessage)));
            stringRedisTemplate.opsForList().rightPushAll(key, values);
            stringRedisTemplate.opsForList().trim(
                    key, -properties.getHistoryMaxMessages(), -1);
            stringRedisTemplate.expire(key, properties.getHistoryTtlDays(), TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("unable to persist assistant conversation history", e);
        }
    }

    private String historyKey(Long userId, String threadId) {
        return HISTORY_KEY_PREFIX + userId + ":" + threadId;
    }
}
