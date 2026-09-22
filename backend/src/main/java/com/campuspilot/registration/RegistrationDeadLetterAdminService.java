package com.campuspilot.registration;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuspilot.dto.Result;
import com.campuspilot.entity.RegistrationDeadLetter;
import com.campuspilot.mapper.RegistrationDeadLetterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 管理端查看与重放报名业务死信。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationDeadLetterAdminService {
    private static final String REPLAYED = "REPLAYED";
    private static final String AUTO_REPLAYED = "AUTO_REPLAYED";
    private static final String AUTO_RETRYING = "AUTO_RETRYING";

    private final RegistrationDeadLetterMapper deadLetterMapper;
    private final RegistrationMessageCodec codec;
    private final RegistrationRequestRepository requestRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final RegistrationMqProperties properties;
    private final RegistrationMetrics metrics;

    /** 分页返回死信记录。 */
    public Result listDeadLetters(Integer current, Integer pageSize) {
        int page = current == null || current < 1 ? 1 : current;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<RegistrationDeadLetter> pageResult = deadLetterMapper.selectPage(
                new Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RegistrationDeadLetter>()
                        .orderByDesc(RegistrationDeadLetter::getCreateTime));
        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("current", pageResult.getCurrent());
        result.put("pages", pageResult.getPages());
        return Result.ok(result);
    }

    /**
     * 重放一条死信：把对应流水重置为 PENDING 并重新走一次事务消息（重新预占名额）。
     * 幂等：已重放或流水状态不允许重置时直接拒绝。
     */
    public Result replay(Long id) {
        return replay(id, false);
    }

    /** 供无人值守任务调用：成功状态标记为 AUTO_REPLAYED。 */
    public Result replayAutomatically(Long id) {
        return replay(id, true);
    }

    private Result replay(Long id, boolean automatic) {
        RegistrationDeadLetter deadLetter = deadLetterMapper.selectById(id);
        if (deadLetter == null) {
            return Result.fail("死信记录不存在");
        }
        if (REPLAYED.equals(deadLetter.getStatus()) || AUTO_REPLAYED.equals(deadLetter.getStatus())) {
            return Result.fail("该死信已处理完成，请勿重复操作");
        }
        if (!automatic && AUTO_RETRYING.equals(deadLetter.getStatus())) {
            return Result.fail("该死信正在自动重放，请稍后刷新");
        }
        RegistrationDeadLetterMessage payload;
        try {
            payload = codec.decodeDeadLetter(deadLetter.getPayload());
        } catch (Exception e) {
            return Result.fail("死信载荷无法解析，请人工处理");
        }
        RegistrationMessage original = payload.getOriginalMessage();
        if (original == null) {
            return Result.fail("死信载荷缺少原始报名消息");
        }
        String newEventId = UUID.randomUUID().toString().replace("-", "");
        int reset = requestRepository.resetForRetry(original.getRegistrationId(), newEventId);
        if (reset == 0) {
            return Result.fail("报名流水已关闭或状态不允许重放，请先人工确认");
        }
        RegistrationMessage retry = new RegistrationMessage(
                newEventId, original.getRegistrationId(), original.getUserId(),
                original.getRegistrationPassId(), System.currentTimeMillis(), 1);
        Message<RegistrationMessage> message = MessageBuilder.withPayload(retry)
                .setHeader(RocketMQHeaders.KEYS, retry.getRegistrationId().toString())
                .setHeader("registrationId", retry.getRegistrationId().toString())
                .build();
        try {
            rocketMQTemplate.sendMessageInTransaction(properties.createDestination(), message, retry);
        } catch (Exception e) {
            log.error("dead letter replay send failed registrationId={} deadLetterId={}",
                    retry.getRegistrationId(), id, e);
            requestRepository.updateStatus(retry.getRegistrationId(), RegistrationStatus.FAILED,
                    "REPLAY_SEND_FAILED", "重放消息发送失败，请稍后重试");
            return Result.fail("重放消息发送失败，流水已置为失败可重新报名");
        }
        deadLetter.setStatus(automatic ? AUTO_REPLAYED : REPLAYED);
        deadLetter.setUpdateTime(java.time.LocalDateTime.now());
        deadLetter.setLastRetryTime(deadLetter.getUpdateTime());
        deadLetter.setNextRetryTime(null);
        deadLetterMapper.updateById(deadLetter);
        metrics.accepted();
        log.info("registration dead letter replayed deadLetterId={} registrationId={} automatic={}",
                id, retry.getRegistrationId(), automatic);
        return Result.ok();
    }
}
