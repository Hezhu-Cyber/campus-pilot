package com.campuspilot.registration;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.registration.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${app.registration.mq.topic:campus-registration}",
        selectorExpression = RegistrationMqConstants.CREATE_TAG,
        consumerGroup = "${app.registration.mq.consumer-group:registration-persist-group}",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY,
        consumeThreadNumber = 4,
        maxReconsumeTimes = RegistrationMqConstants.MAX_RECONSUME_TIMES
)
public class RegistrationMessageConsumer implements RocketMQListener<MessageExt> {
    private final RegistrationMessageCodec codec;
    private final RegistrationPersistenceService persistenceService;
    private final RegistrationReservationService reservationService;
    private final RegistrationRequestRepository requestRepository;
    private final RegistrationFailureService failureService;
    private final RegistrationDeadLetterPublisher deadLetterPublisher;
    private final RegistrationMetrics metrics;

    @Override
    public void onMessage(MessageExt rawMessage) {
        Timer.Sample timer = metrics.startConsume();
        RegistrationMessage message = null;
        try {
            message = codec.decode(rawMessage.getBody());// 1. 把纸条解码成能看懂的对象
            validate(message);//判断字段齐不齐
            requestRepository.updateMessageId(message.getRegistrationId(), rawMessage.getMsgId());
            // 2. 告诉 Redis：这个报名进入"处理中"
            reservationService.markProcessing(message);
            // 3. 正式落库（写数据库）
            persistenceService.persist(message);
            // 4. 落库成功 → Redis 标记"成功"
            reservationService.markSuccess(message);
            metrics.consumed();
            log.info("registration consumed registrationId={} messageId={} reconsumeTimes={}",
                    message.getRegistrationId(), rawMessage.getMsgId(), rawMessage.getReconsumeTimes());
        } catch (RegistrationPermanentException e) {
            if (message == null) {
                log.error("discarding undecodable registration message messageId={}", rawMessage.getMsgId(), e);
                throw e;
            }
            // ① "永久失败"（比如名额不足、流水已关闭）：不用再重试了；放入死信
            moveToBusinessDeadLetter(message, rawMessage, e.getCode(), e.getMessage());
        } catch (Exception e) {
            // ② 其他异常（比如数据库抖了一下）：先重试
            if (message != null) {
                requestRepository.incrementRetry(message.getRegistrationId());
            }
            metrics.retried();
//            设置重试次数为8；若超过次数则直接放入死信中
//            moveToBusinessDeadLetter：做两件事；第一就是把redis占的座退回去；第二就是把整件事写进死信里面去，等人工处理
            if (message != null && rawMessage.getReconsumeTimes() >= RegistrationMqConstants.MAX_RECONSUME_TIMES - 1) {
                moveToBusinessDeadLetter(message, rawMessage, "RETRY_EXHAUSTED", e.getMessage());
                return;
            }
            log.warn("registration consumption will retry registrationId={} messageId={} reconsumeTimes={}",
                    message == null ? null : message.getRegistrationId(), rawMessage.getMsgId(),
                    rawMessage.getReconsumeTimes(), e);
            throw e;
        } finally {
            metrics.stopConsume(timer);
        }
    }

    private void moveToBusinessDeadLetter(RegistrationMessage message, MessageExt rawMessage,
                                          String code, String reason) {
        failureService.failAndCompensate(message, code, reason);
        RegistrationDeadLetterMessage deadLetter = new RegistrationDeadLetterMessage(
                message, rawMessage.getMsgId(), rawMessage.getReconsumeTimes(),
                code, reason, System.currentTimeMillis());
        deadLetterPublisher.publish(deadLetter);
        metrics.deadLettered();
        log.error("registration moved to business dead letter registrationId={} messageId={} code={}",
                message.getRegistrationId(), rawMessage.getMsgId(), code);
    }

    private void validate(RegistrationMessage message) {
        if (message.getSchemaVersion() == null || message.getSchemaVersion() != 1 ||
                message.getEventId() == null || message.getRegistrationId() == null ||
                message.getUserId() == null || message.getRegistrationPassId() == null) {
            throw new RegistrationPermanentException("INVALID_MESSAGE", "报名消息字段不完整或版本不受支持");
        }
    }
}
