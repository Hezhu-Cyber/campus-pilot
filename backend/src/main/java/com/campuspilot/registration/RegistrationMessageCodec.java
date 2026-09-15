package com.campuspilot.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationMessageCodec {
    private final ObjectMapper objectMapper;

    public RegistrationMessage decode(Message<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof RegistrationMessage) {
            return (RegistrationMessage) payload;
        }
        try {
            if (payload instanceof byte[]) {
                return objectMapper.readValue((byte[]) payload, RegistrationMessage.class);
            }
            if (payload instanceof String) {
                return objectMapper.readValue((String) payload, RegistrationMessage.class);
            }
            return objectMapper.convertValue(payload, RegistrationMessage.class);
        } catch (Exception e) {
            throw new RegistrationPermanentException("INVALID_MESSAGE", "报名消息格式不正确");
        }
    }

    public RegistrationMessage decode(byte[] payload) {
        try {
            return objectMapper.readValue(payload, RegistrationMessage.class);
        } catch (Exception e) {
            throw new RegistrationPermanentException("INVALID_MESSAGE", "报名消息格式不正确");
        }
    }

    /** 解析死信表中保存的完整死信载荷。 */
    public RegistrationDeadLetterMessage decodeDeadLetter(String payload) {
        try {
            return objectMapper.readValue(payload, RegistrationDeadLetterMessage.class);
        } catch (Exception e) {
            throw new RegistrationPermanentException("INVALID_PAYLOAD", "死信载荷无法解析");
        }
    }

    public String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize registration message", e);
        }
    }
}
