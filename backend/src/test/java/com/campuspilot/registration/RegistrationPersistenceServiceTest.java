package com.campuspilot.registration;

import com.campuspilot.entity.ActivityRegistration;
import com.campuspilot.entity.RegistrationPass;
import com.campuspilot.entity.RegistrationRequest;
import com.campuspilot.mapper.ActivityRegistrationMapper;
import com.campuspilot.mapper.LimitedRegistrationQuotaMapper;
import com.campuspilot.service.IActivityService;
import com.campuspilot.service.IRegistrationPassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationPersistenceServiceTest {
    @Mock
    private RegistrationRequestRepository requestRepository;
    @Mock
    private ActivityRegistrationMapper registrationMapper;
    @Mock
    private LimitedRegistrationQuotaMapper quotaMapper;
    @Mock
    private IRegistrationPassService passService;
    @Mock
    private IActivityService activityService;

    private RegistrationPersistenceService service;
    private RegistrationMessage message;

    @BeforeEach
    void setUp() {
        service = new RegistrationPersistenceService(
                requestRepository, registrationMapper, quotaMapper, passService, activityService);
        message = new RegistrationMessage("event-1", 100L, 10L, 20L, 1L, 1);
    }

    @Test
    void persistsRegistrationAndDecrementsStockOnce() {
        when(requestRepository.findById(100L)).thenReturn(request(RegistrationStatus.RESERVED));
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(quotaMapper.decrementStock(20L)).thenReturn(1);
        RegistrationPass pass = new RegistrationPass();
        pass.setId(20L);
        pass.setActivityId(1L);
        when(passService.getById(20L)).thenReturn(pass);

        service.persist(message);

        verify(quotaMapper).decrementStock(20L);
        ArgumentCaptor<ActivityRegistration> captor = ArgumentCaptor.forClass(ActivityRegistration.class);
        verify(registrationMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getId());
        assertEquals(10L, captor.getValue().getUserId());
        verify(requestRepository).updateStatus(100L, RegistrationStatus.SUCCESS, null, null);
        verify(activityService).incrementSold(1L);
    }

    @Test
    void successfulDuplicateDeliveryDoesNotDecrementStockAgain() {
        when(requestRepository.findById(100L)).thenReturn(request(RegistrationStatus.SUCCESS));
        service.persist(message);
        verify(quotaMapper, never()).decrementStock(any());
        verify(registrationMapper, never()).insert(any());
        verify(activityService, never()).incrementSold(any());
    }

    @Test
    void databaseStockMismatchIsPermanentFailure() {
        when(requestRepository.findById(100L)).thenReturn(request(RegistrationStatus.RESERVED));
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(quotaMapper.decrementStock(20L)).thenReturn(0);
        RegistrationPermanentException exception = assertThrows(
                RegistrationPermanentException.class, () -> service.persist(message));
        assertEquals("DATABASE_STOCK_INSUFFICIENT", exception.getCode());
        verify(registrationMapper, never()).insert(any());
        verify(activityService, never()).incrementSold(any());
    }

    @Test
    void restoreCancelledRegistrationIncrementsSold() {
        ActivityRegistration cancelled = new ActivityRegistration();
        cancelled.setId(100L);
        cancelled.setUserId(10L);
        cancelled.setRegistrationPassId(20L);
        cancelled.setStatus(4);
        when(requestRepository.findById(100L)).thenReturn(request(RegistrationStatus.RESERVED));
        when(registrationMapper.selectOne(any())).thenReturn(cancelled);
        when(quotaMapper.decrementStock(20L)).thenReturn(1);
        RegistrationPass pass = new RegistrationPass();
        pass.setId(20L);
        pass.setActivityId(1L);
        when(passService.getById(20L)).thenReturn(pass);

        service.persist(message);

        verify(registrationMapper).updateById(cancelled);
        assertEquals(2, cancelled.getStatus());
        verify(activityService).incrementSold(1L);
    }

    private RegistrationRequest request(RegistrationStatus status) {
        RegistrationRequest request = new RegistrationRequest();
        request.setRegistrationId(100L);
        request.setUserId(10L);
        request.setRegistrationPassId(20L);
        request.setStatus(status.name());
        return request;
    }
}
