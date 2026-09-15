package com.campuspilot.registration;

import com.campuspilot.dto.Result;
import com.campuspilot.entity.RegistrationDeadLetter;
import com.campuspilot.mapper.RegistrationDeadLetterMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationDeadLetterAdminServiceTest {
    @Mock
    private RegistrationDeadLetterMapper deadLetterMapper;
    @Mock
    private RegistrationMessageCodec codec;
    @Mock
    private RegistrationRequestRepository requestRepository;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RegistrationMqProperties properties;
    @Mock
    private RegistrationMetrics metrics;

    private RegistrationDeadLetterAdminService service;

    @BeforeEach
    void setUp() {
        when(properties.createDestination()).thenReturn("campus-registration:REGISTRATION_CREATE");
        service = new RegistrationDeadLetterAdminService(
                deadLetterMapper, codec, requestRepository, rocketMQTemplate, properties, metrics);
    }

    @Test
    void replayResetsRequestAndResendsTransactionMessage() {
        RegistrationDeadLetter deadLetter = deadLetter("payload-1", "PENDING");
        when(deadLetterMapper.selectById(1L)).thenReturn(deadLetter);
        RegistrationMessage original = new RegistrationMessage("event-1", 100L, 10L, 20L, 1L, 1);
        RegistrationDeadLetterMessage payload = new RegistrationDeadLetterMessage(
                original, "msg-1", 7, "RETRY_EXHAUSTED", "timeout", 1L);
        when(codec.decodeDeadLetter("payload-1")).thenReturn(payload);
        when(requestRepository.resetForRetry(eq(100L), anyString())).thenReturn(1);

        Result result = service.replay(1L);

        assertEquals(true, result.getSuccess());
        verify(requestRepository).resetForRetry(eq(100L), anyString());
        verify(rocketMQTemplate).sendMessageInTransaction(anyString(), any(Message.class), any(RegistrationMessage.class));
        assertEquals("REPLAYED", deadLetter.getStatus());
        verify(deadLetterMapper).updateById(deadLetter);
    }

    @Test
    void replayRejectsWhenRequestCannotBeReset() {
        RegistrationDeadLetter deadLetter = deadLetter("payload-1", "PENDING");
        when(deadLetterMapper.selectById(1L)).thenReturn(deadLetter);
        RegistrationMessage original = new RegistrationMessage("event-1", 100L, 10L, 20L, 1L, 1);
        RegistrationDeadLetterMessage payload = new RegistrationDeadLetterMessage(
                original, "msg-1", 7, "RETRY_EXHAUSTED", "timeout", 1L);
        when(codec.decodeDeadLetter("payload-1")).thenReturn(payload);
        when(requestRepository.resetForRetry(eq(100L), anyString())).thenReturn(0);

        Result result = service.replay(1L);

        assertFalse(result.getSuccess());
        verify(rocketMQTemplate, never())
                .sendMessageInTransaction(anyString(), any(Message.class), any(RegistrationMessage.class));
    }

    @Test
    void replayRejectsAlreadyReplayed() {
        RegistrationDeadLetter deadLetter = deadLetter("payload-1", "REPLAYED");
        when(deadLetterMapper.selectById(1L)).thenReturn(deadLetter);

        Result result = service.replay(1L);

        assertFalse(result.getSuccess());
        verify(requestRepository, never()).resetForRetry(anyLong(), anyString());
    }

    private RegistrationDeadLetter deadLetter(String payload, String status) {
        RegistrationDeadLetter deadLetter = new RegistrationDeadLetter();
        deadLetter.setId(1L);
        deadLetter.setPayload(payload);
        deadLetter.setStatus(status);
        return deadLetter;
    }
}
