package com.campuspilot.registration;

import com.campuspilot.entity.RegistrationDeadLetter;
import com.campuspilot.mapper.RegistrationDeadLetterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.registration.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${app.registration.mq.dead-letter-topic:campus-registration-dead-letter}",
        selectorExpression = RegistrationMqConstants.DEAD_LETTER_TAG,
        consumerGroup = "${app.registration.mq.dead-letter-consumer-group:registration-dead-letter-store-group}",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY,
        consumeThreadNumber = 1
)
public class RegistrationDeadLetterConsumer implements RocketMQListener<RegistrationDeadLetterMessage> {
    private final RegistrationDeadLetterMapper mapper;
    private final RegistrationMessageCodec codec;
    private final RegistrationMqProperties properties;

    @Override
    public void onMessage(RegistrationDeadLetterMessage message) {
        RegistrationDeadLetter record = new RegistrationDeadLetter();
        record.setEventId(message.getOriginalMessage().getEventId());
        record.setRegistrationId(message.getOriginalMessage().getRegistrationId());
        record.setMessageId(message.getRocketMqMessageId());
        record.setReconsumeTimes(message.getReconsumeTimes());
        record.setRetryCount(0);
        record.setNextRetryTime(LocalDateTime.now()
                .plusSeconds(properties.getDeadLetterAutoRetryInitialDelaySeconds()));
        record.setFailureCode(message.getFailureCode());
        record.setFailureReason(message.getFailureReason());
        record.setPayload(codec.encode(message));
        record.setStatus(RegistrationDeadLetterStatus.PENDING.name());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException ignored) {
            log.info("duplicate dead letter ignored eventId={}", record.getEventId());
        }
        log.error("registration dead letter stored registrationId={} eventId={} code={}",
                record.getRegistrationId(), record.getEventId(), record.getFailureCode());
    }
}
