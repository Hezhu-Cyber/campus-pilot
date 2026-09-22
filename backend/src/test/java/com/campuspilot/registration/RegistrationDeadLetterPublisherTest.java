package com.campuspilot.registration;

import com.campuspilot.entity.RegistrationDeadLetter;
import com.campuspilot.mapper.RegistrationDeadLetterMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationDeadLetterPublisherTest {
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RegistrationMqProperties properties;
    @Mock
    private RegistrationDeadLetterMapper deadLetterMapper;
    @Mock
    private RegistrationMessageCodec codec;

    private RegistrationDeadLetterPublisher publisher;
    private RegistrationDeadLetterMessage payload;

    @BeforeEach
    void setUp() {
        publisher = new RegistrationDeadLetterPublisher(
                rocketMQTemplate, properties, deadLetterMapper, codec);
        RegistrationMessage original = new RegistrationMessage("event-1", 100L, 10L, 20L, 1L, 1);
        payload = new RegistrationDeadLetterMessage(
                original, "msg-1", 7, "RETRY_EXHAUSTED", "timeout", 1L);
        when(properties.deadLetterDestination()).thenReturn("dead-letter:REGISTRATION_FAILED");
    }

    @Test
    void storesLocallyWhenDeadLetterTopicSendFails() {
        when(properties.getDeadLetterAutoRetryInitialDelaySeconds()).thenReturn(60);
        when(codec.encode(payload)).thenReturn("payload-json");
        doThrow(new IllegalStateException("mq unavailable"))
                .when(rocketMQTemplate).syncSend(anyString(), any(Message.class), anyLong());

        publisher.publish(payload);

        ArgumentCaptor<RegistrationDeadLetter> captor =
                ArgumentCaptor.forClass(RegistrationDeadLetter.class);
        verify(deadLetterMapper).insert(captor.capture());
        RegistrationDeadLetter record = captor.getValue();
        assertEquals("event-1", record.getEventId());
        assertEquals(100L, record.getRegistrationId());
        assertEquals("PENDING", record.getStatus());
        assertEquals("payload-json", record.getPayload());
    }

    @Test
    void doesNotWriteLocalCopyWhenTopicSendSucceeds() {
        publisher.publish(payload);

        verify(deadLetterMapper, never()).insert(any(RegistrationDeadLetter.class));
    }
}
