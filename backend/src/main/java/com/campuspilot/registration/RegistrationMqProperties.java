package com.campuspilot.registration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.registration.mq")
public class RegistrationMqProperties {
    private boolean enabled = true;
    private String topic = "campus-registration";
    private String consumerGroup = "registration-persist-group";
    private String deadLetterTopic = "campus-registration-dead-letter";
    private String deadLetterConsumerGroup = "registration-dead-letter-store-group";
    private long transactionStateTtlSeconds = 604800L;
    private long staleRequestSeconds = 30L;
    private long reconcileFixedDelayMillis = 60000L;

    public String createDestination() {
        return topic + ":" + RegistrationMqConstants.CREATE_TAG;
    }

    public String deadLetterDestination() {
        return deadLetterTopic + ":" + RegistrationMqConstants.DEAD_LETTER_TAG;
    }
}
