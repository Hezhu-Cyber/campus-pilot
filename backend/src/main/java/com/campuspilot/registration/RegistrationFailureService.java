package com.campuspilot.registration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationFailureService {
    private final RegistrationRequestRepository requestRepository;
    private final RegistrationPersistenceService persistenceService;
    private final RegistrationReservationService reservationService;
    private final RegistrationMetrics metrics;

    public void failAndCompensate(RegistrationMessage message, String code, String reason) {
        if (persistenceService.registrationExists(message)) {
            requestRepository.updateStatus(message.getRegistrationId(), RegistrationStatus.SUCCESS, null, null);
            reservationService.markSuccess(message);
            return;
        }
        requestRepository.updateStatus(message.getRegistrationId(), RegistrationStatus.FAILED, code, reason);
        boolean compensated = reservationService.compensate(message);
        if (!compensated) {
            String state = reservationService.transactionState(
                    message.getRegistrationPassId(), message.getRegistrationId());
            if (!RegistrationStatus.COMPENSATED.name().equals(state)) {
                throw new IllegalStateException("Registration inventory compensation did not complete");
            }
        }
        requestRepository.updateStatus(message.getRegistrationId(), RegistrationStatus.COMPENSATED, code, reason);
        metrics.compensated();
        log.warn("registration compensated registrationId={} userId={} passId={} code={}",
                message.getRegistrationId(), message.getUserId(), message.getRegistrationPassId(), code);
    }
}
