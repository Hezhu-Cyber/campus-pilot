package com.campuspilot.registration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuspilot.dto.Result;
import com.campuspilot.entity.RegistrationDeadLetter;
import com.campuspilot.mapper.RegistrationDeadLetterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 无人值守死信处理任务：只自动重放可恢复错误，永久错误转人工等待。
 * 多实例通过条件更新抢占记录；实例崩溃后由 claim timeout 重新放回队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.registration.mq", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RegistrationDeadLetterRetryJob {
    private final RegistrationDeadLetterMapper deadLetterMapper;
    private final RegistrationDeadLetterAdminService replayService;
    private final RegistrationMqProperties properties;
    private final RegistrationMetrics metrics;
    private final RegistrationDeadLetterNotifier notifier;

    @Scheduled(fixedDelayString =
            "${app.registration.mq.dead-letter-auto-retry-fixed-delay-millis:60000}")
    public void retryPendingDeadLetters() {
        LocalDateTime now = LocalDateTime.now();
        try {
            if (properties.isDeadLetterAutoRetryEnabled()) {
                recoverStaleClaims(now);
                List<RegistrationDeadLetter> candidates = findCandidates(now);
                for (RegistrationDeadLetter candidate : candidates) {
                    if (!isAutoRetryable(candidate.getFailureCode())) {
                        markManualRequired(candidate, RegistrationDeadLetterStatus.PENDING, now,
                                currentRetryCount(candidate), "失败码不在自动重试白名单中");
                        continue;
                    }
                    if (!claim(candidate, now)) {
                        continue;
                    }
                    replayOne(candidate, now);
                }
            }
        } catch (Exception e) {
            log.error("registration dead letter auto retry job failed", e);
        } finally {
            updateBacklogMetrics(now);
        }
    }

    private List<RegistrationDeadLetter> findCandidates(LocalDateTime now) {
        LocalDateTime initialBefore = now.minusSeconds(
                Math.max(0, properties.getDeadLetterAutoRetryInitialDelaySeconds()));
        int batchSize = Math.max(1, Math.min(properties.getDeadLetterAutoRetryBatchSize(), 200));
        return deadLetterMapper.selectList(new LambdaQueryWrapper<RegistrationDeadLetter>()
                .eq(RegistrationDeadLetter::getStatus, RegistrationDeadLetterStatus.PENDING.name())
                .le(RegistrationDeadLetter::getCreateTime, initialBefore)
                .and(wrapper -> wrapper.isNull(RegistrationDeadLetter::getNextRetryTime)
                        .or().le(RegistrationDeadLetter::getNextRetryTime, now))
                .orderByAsc(RegistrationDeadLetter::getCreateTime)
                .last("LIMIT " + batchSize));
    }

    private boolean claim(RegistrationDeadLetter deadLetter, LocalDateTime now) {
        int updated = deadLetterMapper.update(null, new LambdaUpdateWrapper<RegistrationDeadLetter>()
                .eq(RegistrationDeadLetter::getId, deadLetter.getId())
                .eq(RegistrationDeadLetter::getStatus, RegistrationDeadLetterStatus.PENDING.name())
                .and(wrapper -> wrapper.isNull(RegistrationDeadLetter::getNextRetryTime)
                        .or().le(RegistrationDeadLetter::getNextRetryTime, now))
                .set(RegistrationDeadLetter::getStatus, RegistrationDeadLetterStatus.AUTO_RETRYING.name())
                .set(RegistrationDeadLetter::getUpdateTime, now));
        return updated > 0;
    }

    private void replayOne(RegistrationDeadLetter deadLetter, LocalDateTime startedAt) {
        Result result;
        try {
            result = replayService.replayAutomatically(deadLetter.getId());
        } catch (Exception e) {
            result = Result.fail(e.getMessage());
            log.error("registration dead letter automatic replay threw deadLetterId={} registrationId={}",
                    deadLetter.getId(), deadLetter.getRegistrationId(), e);
        }

        int attempts = currentRetryCount(deadLetter) + 1;
        if (Boolean.TRUE.equals(result.getSuccess())) {
            deadLetterMapper.update(null, new LambdaUpdateWrapper<RegistrationDeadLetter>()
                    .eq(RegistrationDeadLetter::getId, deadLetter.getId())
                    .eq(RegistrationDeadLetter::getStatus,
                            RegistrationDeadLetterStatus.AUTO_REPLAYED.name())
                    .set(RegistrationDeadLetter::getRetryCount, attempts)
                    .set(RegistrationDeadLetter::getLastRetryTime, startedAt)
                    .set(RegistrationDeadLetter::getNextRetryTime, null)
                    .set(RegistrationDeadLetter::getUpdateTime, LocalDateTime.now()));
            metrics.deadLetterAutoReplayed();
            log.info("registration dead letter automatically replayed deadLetterId={} registrationId={} attempts={}",
                    deadLetter.getId(), deadLetter.getRegistrationId(), attempts);
            return;
        }

        metrics.deadLetterAutoRetryFailed();
        if (attempts >= Math.max(1, properties.getDeadLetterAutoRetryMaxAttempts())) {
            markManualRequired(deadLetter, RegistrationDeadLetterStatus.AUTO_RETRYING,
                    LocalDateTime.now(), attempts, result.getErrorMsg());
            log.error("registration dead letter automatic retry exhausted deadLetterId={} registrationId={} attempts={} error={}",
                    deadLetter.getId(), deadLetter.getRegistrationId(), attempts, result.getErrorMsg());
            return;
        }

        long backoffSeconds = retryBackoffSeconds(attempts);
        LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(backoffSeconds);
        deadLetterMapper.update(null, new LambdaUpdateWrapper<RegistrationDeadLetter>()
                .eq(RegistrationDeadLetter::getId, deadLetter.getId())
                .eq(RegistrationDeadLetter::getStatus, RegistrationDeadLetterStatus.AUTO_RETRYING.name())
                .set(RegistrationDeadLetter::getStatus, RegistrationDeadLetterStatus.PENDING.name())
                .set(RegistrationDeadLetter::getRetryCount, attempts)
                .set(RegistrationDeadLetter::getLastRetryTime, LocalDateTime.now())
                .set(RegistrationDeadLetter::getNextRetryTime, nextRetryTime)
                .set(RegistrationDeadLetter::getUpdateTime, LocalDateTime.now()));
        log.warn("registration dead letter automatic retry scheduled deadLetterId={} registrationId={} attempts={} nextRetryTime={} error={}",
                deadLetter.getId(), deadLetter.getRegistrationId(), attempts, nextRetryTime,
                result.getErrorMsg());
    }

    private boolean markManualRequired(RegistrationDeadLetter deadLetter,
                                       RegistrationDeadLetterStatus expectedStatus,
                                       LocalDateTime now, int attempts, String reason) {
        int updated = deadLetterMapper.update(null, new LambdaUpdateWrapper<RegistrationDeadLetter>()
                .eq(RegistrationDeadLetter::getId, deadLetter.getId())
                .eq(RegistrationDeadLetter::getStatus, expectedStatus.name())
                .set(RegistrationDeadLetter::getStatus,
                        RegistrationDeadLetterStatus.MANUAL_REQUIRED.name())
                .set(RegistrationDeadLetter::getRetryCount, attempts)
                .set(RegistrationDeadLetter::getNextRetryTime, null)
                .set(RegistrationDeadLetter::getLastRetryTime,
                        expectedStatus == RegistrationDeadLetterStatus.AUTO_RETRYING ? now : null)
                .set(RegistrationDeadLetter::getUpdateTime, now));
        if (updated > 0) {
            metrics.deadLetterManualRequired();
            log.error("registration dead letter requires manual handling deadLetterId={} registrationId={} code={}",
                    deadLetter.getId(), deadLetter.getRegistrationId(), deadLetter.getFailureCode());
            notifier.notifyManualRequired(deadLetter, attempts,
                    reason == null || reason.trim().isEmpty() ? "AUTO_RETRY_EXHAUSTED" : reason);
            return true;
        }
        return false;
    }

    private void recoverStaleClaims(LocalDateTime now) {
        LocalDateTime staleBefore = now.minusSeconds(
                Math.max(1L, properties.getDeadLetterAutoRetryClaimTimeoutSeconds()));
        int batchSize = Math.max(1, Math.min(properties.getDeadLetterAutoRetryBatchSize(), 200));
        List<RegistrationDeadLetter> staleClaims = deadLetterMapper.selectList(
                new LambdaQueryWrapper<RegistrationDeadLetter>()
                        .eq(RegistrationDeadLetter::getStatus,
                                RegistrationDeadLetterStatus.AUTO_RETRYING.name())
                        .lt(RegistrationDeadLetter::getUpdateTime, staleBefore)
                        .orderByAsc(RegistrationDeadLetter::getUpdateTime)
                        .last("LIMIT " + batchSize));
        for (RegistrationDeadLetter deadLetter : staleClaims) {
            int updated = deadLetterMapper.update(null, new LambdaUpdateWrapper<RegistrationDeadLetter>()
                    .eq(RegistrationDeadLetter::getId, deadLetter.getId())
                    .eq(RegistrationDeadLetter::getStatus,
                            RegistrationDeadLetterStatus.AUTO_RETRYING.name())
                    .lt(RegistrationDeadLetter::getUpdateTime, staleBefore)
                    .set(RegistrationDeadLetter::getStatus,
                            RegistrationDeadLetterStatus.PENDING.name())
                    .set(RegistrationDeadLetter::getNextRetryTime, now)
                    .set(RegistrationDeadLetter::getUpdateTime, now));
            if (updated > 0) {
                log.warn("stale registration dead letter claim recovered deadLetterId={} registrationId={}",
                        deadLetter.getId(), deadLetter.getRegistrationId());
            }
        }
    }

    private boolean isAutoRetryable(String failureCode) {
        List<String> retryableCodes = properties.getDeadLetterAutoRetryFailureCodes();
        return failureCode != null && retryableCodes != null && retryableCodes.contains(failureCode);
    }

    private int currentRetryCount(RegistrationDeadLetter deadLetter) {
        return deadLetter.getRetryCount() == null ? 0 : Math.max(0, deadLetter.getRetryCount());
    }

    private long retryBackoffSeconds(int attempts) {
        long base = Math.max(1L, properties.getDeadLetterAutoRetryBackoffSeconds());
        long max = Math.max(base, properties.getDeadLetterAutoRetryMaxBackoffSeconds());
        long delay = base;
        for (int i = 1; i < attempts && delay < max; i++) {
            if (delay > max / 2) {
                return max;
            }
            delay *= 2;
        }
        return Math.min(delay, max);
    }

    private void updateBacklogMetrics(LocalDateTime now) {
        try {
            List<String> pendingStatuses = Arrays.asList(
                    RegistrationDeadLetterStatus.PENDING.name(),
                    RegistrationDeadLetterStatus.AUTO_RETRYING.name());
            Integer pending = deadLetterMapper.selectCount(new LambdaQueryWrapper<RegistrationDeadLetter>()
                    .in(RegistrationDeadLetter::getStatus, pendingStatuses));
            Integer manualRequired = deadLetterMapper.selectCount(
                    new LambdaQueryWrapper<RegistrationDeadLetter>()
                            .eq(RegistrationDeadLetter::getStatus,
                                    RegistrationDeadLetterStatus.MANUAL_REQUIRED.name()));
            RegistrationDeadLetter oldest = deadLetterMapper.selectOne(
                    new LambdaQueryWrapper<RegistrationDeadLetter>()
                            .in(RegistrationDeadLetter::getStatus, pendingStatuses)
                            .orderByAsc(RegistrationDeadLetter::getCreateTime)
                            .last("LIMIT 1"));
            long oldestSeconds = oldest == null || oldest.getCreateTime() == null
                    ? 0L : Math.max(0L, Duration.between(oldest.getCreateTime(), now).getSeconds());
            metrics.updateDeadLetterBacklog(
                    pending == null ? 0L : pending,
                    manualRequired == null ? 0L : manualRequired,
                    oldestSeconds);
        } catch (Exception e) {
            log.warn("failed to refresh registration dead letter backlog metrics", e);
        }
    }
}
