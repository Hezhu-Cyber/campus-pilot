package com.campuspilot.registration;

import com.campuspilot.entity.RegistrationDeadLetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将最终需要人工处理的死信推送到外部告警渠道。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.registration.mq", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RegistrationDeadLetterNotifier {
    private final RegistrationMqProperties properties;

    public void notifyManualRequired(RegistrationDeadLetter deadLetter, int attempts, String reason) {
        String webhookUrl = properties.getDeadLetterAlertWebhookUrl();
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            log.error("registration dead letter manual alert disabled deadLetterId={} registrationId={} reason={}",
                    deadLetter.getId(), deadLetter.getRegistrationId(), reason);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "REGISTRATION_DEAD_LETTER_MANUAL_REQUIRED");
        payload.put("deadLetterId", deadLetter.getId());
        payload.put("registrationId", deadLetter.getRegistrationId());
        payload.put("failureCode", deadLetter.getFailureCode());
        payload.put("failureReason", deadLetter.getFailureReason());
        payload.put("autoRetryAttempts", attempts);
        payload.put("manualReason", reason);
        payload.put("occurredAt", LocalDateTime.now().toString());
        payload.put("adminPath", "/admin/registration/dead-letters");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate().postForEntity(webhookUrl, new HttpEntity<>(payload, headers), String.class);
            log.info("registration dead letter manual alert sent deadLetterId={} registrationId={}",
                    deadLetter.getId(), deadLetter.getRegistrationId());
        } catch (Exception e) {
            log.error("registration dead letter manual alert failed deadLetterId={} registrationId={}",
                    deadLetter.getId(), deadLetter.getRegistrationId(), e);
        }
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(100, properties.getDeadLetterAlertConnectTimeoutMillis()));
        factory.setReadTimeout(Math.max(100, properties.getDeadLetterAlertReadTimeoutMillis()));
        return new RestTemplate(factory);
    }
}
