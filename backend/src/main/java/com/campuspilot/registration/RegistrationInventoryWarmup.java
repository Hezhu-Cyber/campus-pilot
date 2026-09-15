package com.campuspilot.registration;

import com.campuspilot.entity.LimitedRegistrationQuota;
import com.campuspilot.service.ILimitedRegistrationQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationInventoryWarmup {
    private final ILimitedRegistrationQuotaService quotaService;
    private final RegistrationReservationService reservationService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeMissingKeys() {
        List<LimitedRegistrationQuota> quotas = quotaService.list();
        for (LimitedRegistrationQuota quota : quotas) {
            reservationService.initializeIfAbsent(quota);
        }
        log.info("registration inventory metadata warmed up quotaCount={}", quotas.size());
    }
}
