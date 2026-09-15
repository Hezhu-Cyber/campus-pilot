package com.campuspilot.registration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationFailureServiceTest {
    @Mock
    private RegistrationRequestRepository requestRepository;
    @Mock
    private RegistrationPersistenceService persistenceService;
    @Mock
    private RegistrationReservationService reservationService;
    @Mock
    private RegistrationMetrics metrics;

    private RegistrationFailureService service;
    private RegistrationMessage message;

    @BeforeEach
    void setUp() {
        service = new RegistrationFailureService(
                requestRepository, persistenceService, reservationService, metrics);
        message = new RegistrationMessage("event-1", 100L, 10L, 20L, 1L, 1);
    }

    @Test
    void doesNotCompensateWhenDatabaseRegistrationAlreadyExists() {
        when(persistenceService.registrationExists(message)).thenReturn(true);
        service.failAndCompensate(message, "RETRY_EXHAUSTED", "timeout");
        verify(requestRepository).updateStatus(100L, RegistrationStatus.SUCCESS, null, null);
        verify(reservationService).markSuccess(message);
        verify(reservationService, never()).compensate(message);
    }

    @Test
    void compensationIsPersistedAsTerminalState() {
        when(persistenceService.registrationExists(message)).thenReturn(false);
        when(reservationService.compensate(message)).thenReturn(true);
        service.failAndCompensate(message, "BAD_DATA", "invalid");
        verify(requestRepository).updateStatus(100L, RegistrationStatus.FAILED, "BAD_DATA", "invalid");
        verify(requestRepository).updateStatus(100L, RegistrationStatus.COMPENSATED, "BAD_DATA", "invalid");
        verify(metrics).compensated();
    }
}
