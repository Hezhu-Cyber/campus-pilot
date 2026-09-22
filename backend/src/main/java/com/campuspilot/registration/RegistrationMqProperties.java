package com.campuspilot.registration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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
    private boolean deadLetterAutoRetryEnabled = true;
    private long deadLetterAutoRetryFixedDelayMillis = 60000L;
    private int deadLetterAutoRetryInitialDelaySeconds = 60;
    private int deadLetterAutoRetryMaxAttempts = 3;
    private long deadLetterAutoRetryBackoffSeconds = 60L;
    private long deadLetterAutoRetryMaxBackoffSeconds = 3600L;
    private long deadLetterAutoRetryClaimTimeoutSeconds = 300L;
    private int deadLetterAutoRetryBatchSize = 20;
    private List<String> deadLetterAutoRetryFailureCodes =
            Collections.singletonList("RETRY_EXHAUSTED");
    private String deadLetterAlertWebhookUrl = "";
    private int deadLetterAlertConnectTimeoutMillis = 2000;
    private int deadLetterAlertReadTimeoutMillis = 3000;

    public String createDestination() {
        return topic + ":" + RegistrationMqConstants.CREATE_TAG;
    }

    public String deadLetterDestination() {
        return deadLetterTopic + ":" + RegistrationMqConstants.DEAD_LETTER_TAG;
    }
}
