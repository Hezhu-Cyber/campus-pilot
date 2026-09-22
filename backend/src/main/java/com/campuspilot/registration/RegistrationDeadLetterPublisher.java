package com.campuspilot.registration;

import com.campuspilot.entity.RegistrationDeadLetter;
import com.campuspilot.mapper.RegistrationDeadLetterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationDeadLetterPublisher {
    private final RocketMQTemplate rocketMQTemplate;
    private final RegistrationMqProperties properties;
    private final RegistrationDeadLetterMapper deadLetterMapper;
    private final RegistrationMessageCodec codec;

    public void publish(RegistrationDeadLetterMessage payload) {
        Message<RegistrationDeadLetterMessage> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, payload.getOriginalMessage().getEventId())
                .build();
        try {
            rocketMQTemplate.syncSend(properties.deadLetterDestination(), message, 3000L);
        } catch (Exception mqException) {
            storeLocally(payload, mqException);
        }
    }

    /** MQ 不可用时直接落库，确保补偿后的异常报名仍可追踪和自动重试。 */
    private void storeLocally(RegistrationDeadLetterMessage payload, Exception mqException) {
        RegistrationDeadLetter record = new RegistrationDeadLetter();
        record.setEventId(payload.getOriginalMessage().getEventId());
        record.setRegistrationId(payload.getOriginalMessage().getRegistrationId());
        record.setMessageId(payload.getRocketMqMessageId());
        record.setReconsumeTimes(payload.getReconsumeTimes());
        record.setRetryCount(0);
        record.setNextRetryTime(LocalDateTime.now()
                .plusSeconds(properties.getDeadLetterAutoRetryInitialDelaySeconds()));
        record.setFailureCode(payload.getFailureCode());
        record.setFailureReason(payload.getFailureReason());
        record.setPayload(codec.encode(payload));
        record.setStatus(RegistrationDeadLetterStatus.PENDING.name());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        try {
            deadLetterMapper.insert(record);
            log.error("dead letter topic unavailable; stored locally registrationId={} eventId={}",
                    record.getRegistrationId(), record.getEventId(), mqException);
        } catch (DuplicateKeyException duplicate) {
            log.info("duplicate dead letter ignored during local fallback eventId={}", record.getEventId());
        } catch (Exception dbException) {
            dbException.addSuppressed(mqException);
            throw new IllegalStateException("Failed to persist dead letter locally", dbException);
        }
    }
}
