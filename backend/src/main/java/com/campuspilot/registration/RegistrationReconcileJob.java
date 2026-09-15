package com.campuspilot.registration;

import com.campuspilot.entity.RegistrationRequest;
import com.campuspilot.utils.SimpleRedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.registration.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationReconcileJob {
    private final RegistrationMqProperties properties;
    private final RegistrationRequestRepository requestRepository;
    private final RegistrationPersistenceService persistenceService;
    private final RegistrationReservationService reservationService;
    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelayString = "${app.registration.mq.reconcile-fixed-delay-millis:60000}")
    public void reconcile() {

        SimpleRedisLock lock = new SimpleRedisLock(":registration:reconcile", redisTemplate);
        long leaseSeconds = Math.max(10L, properties.getReconcileFixedDelayMillis() / 1000L - 5L);
        // 拿一把分布式锁，保证多台服务器只有一台在巡逻
        if (!lock.tryLock(leaseSeconds)) {
            return;
        }
        try {
            LocalDateTime before = LocalDateTime.now().minusSeconds(properties.getStaleRequestSeconds());
            List<RegistrationRequest> requests = new ArrayList<>();
            // 找所有卡住超过 30 秒的流水（PENDING/RESERVED/PROCESSING）
            requests.addAll(requestRepository.findStale(before, 100));
            requests.addAll(requestRepository.findCancelledUncompensated(before, 100));
            for (RegistrationRequest request : requests) {
                reconcileOne(request);
            }
            if (!requests.isEmpty()) {
                log.info("registration reconciliation completed staleCount={}", requests.size());
            }
        } finally {
            lock.unlock();
        }
    }

    // 数据库里已经有报名记录了？→ 标记成功（善后）
    // Redis 里是"已占座/处理中"？→ 把纸条重新投一次（resend）
    // 还是 PENDING（一直没占座）？→ 标记失败 TRANSACTION_TIMEOUT
    private void reconcileOne(RegistrationRequest request) {
        RegistrationMessage message = toMessage(request);
        try {
            if (RegistrationStatus.COMPENSATED.name().equals(request.getStatus())
                    && "USER_CANCELLED".equals(request.getFailureCode())) {
                // 用户取消已提交，但 Redis 回补可能在提交后失败：幂等重放回补。
                reconcileCancelledRollback(request);
                return;
            }
            if (persistenceService.registrationExists(message)) {
                requestRepository.updateStatus(request.getRegistrationId(), RegistrationStatus.SUCCESS, null, null);
                reservationService.markSuccess(message);
                return;
            }
            String redisState = reservationService.transactionState(
                    request.getRegistrationPassId(), request.getRegistrationId());
            if (RegistrationStatus.RESERVED.name().equals(redisState) ||
                    RegistrationStatus.PROCESSING.name().equals(redisState)) {
                requestRepository.updateStatus(request.getRegistrationId(), RegistrationStatus.RESERVED, null, null);
                resend(message);
                return;
            }
            if (RegistrationStatus.PENDING.name().equals(request.getStatus())) {
                requestRepository.updateStatus(request.getRegistrationId(), RegistrationStatus.FAILED,
                        "TRANSACTION_TIMEOUT", "事务消息长时间未完成资格预占");
            }
        } catch (Exception e) {
            log.error("registration reconciliation failed registrationId={}", request.getRegistrationId(), e);
        }
    }

    /** 事务状态键仍存在说明取消后的 Redis 回补未完成，幂等重放 rollback 脚本。 */
    private void reconcileCancelledRollback(RegistrationRequest request) {
        String redisState = reservationService.transactionState(
                request.getRegistrationPassId(), request.getRegistrationId());
        if (redisState == null) {
            return;
        }
        boolean rolledBack = reservationService.rollback(
                request.getRegistrationPassId(), request.getUserId(), request.getRegistrationId());
        if (rolledBack) {
            log.warn("cancelled registration redis rollback replayed registrationId={}",
                    request.getRegistrationId());
        } else {
            log.error("cancelled registration redis rollback still failing registrationId={}",
                    request.getRegistrationId());
        }
    }

    private void resend(RegistrationMessage payload) {
        Message<RegistrationMessage> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, payload.getRegistrationId().toString())
                .build();
        rocketMQTemplate.syncSend(properties.createDestination(), message, 3000L);
        log.warn("stale registration message resent registrationId={}", payload.getRegistrationId());
    }

    private RegistrationMessage toMessage(RegistrationRequest request) {
        return new RegistrationMessage(request.getEventId(), request.getRegistrationId(), request.getUserId(),
                request.getRegistrationPassId(), request.getCreateTime() == null
                ? System.currentTimeMillis()
                : java.sql.Timestamp.valueOf(request.getCreateTime()).getTime(), 1);
    }
}
