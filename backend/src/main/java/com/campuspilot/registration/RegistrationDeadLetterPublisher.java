package com.campuspilot.registration;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationDeadLetterPublisher {
    private final RocketMQTemplate rocketMQTemplate;
    private final RegistrationMqProperties properties;

    public void publish(RegistrationDeadLetterMessage payload) {
        Message<RegistrationDeadLetterMessage> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, payload.getOriginalMessage().getEventId())
                .build();
        rocketMQTemplate.syncSend(properties.deadLetterDestination(), message, 3000L);
    }
}
