package com.campuspilot.registration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RegistrationMetrics {
    private final Counter accepted;
    private final Counter rejected;
    private final Counter sendFailed;
    private final Counter consumed;
    private final Counter retried;
    private final Counter deadLettered;
    private final Counter deadLetterAutoReplayed;
    private final Counter deadLetterAutoRetryFailed;
    private final Counter deadLetterManualRequired;
    private final Counter compensated;
    private final Timer consumeTimer;
    private final AtomicLong deadLetterPending = new AtomicLong();
    private final AtomicLong deadLetterManualRequiredCount = new AtomicLong();
    private final AtomicLong deadLetterOldestPendingSeconds = new AtomicLong();

    public RegistrationMetrics(MeterRegistry registry) {
        this.accepted = registry.counter("registration.request.accepted");
        this.rejected = registry.counter("registration.request.rejected");
        this.sendFailed = registry.counter("registration.message.send.failed");
        this.consumed = registry.counter("registration.message.consume.success");
        this.retried = registry.counter("registration.message.consume.retry");
        this.deadLettered = registry.counter("registration.message.dead_letter");
        this.deadLetterAutoReplayed = registry.counter("registration.dead_letter.auto_replayed");
        this.deadLetterAutoRetryFailed = registry.counter("registration.dead_letter.auto_retry_failed");
        this.deadLetterManualRequired = registry.counter("registration.dead_letter.manual_required");
        this.compensated = registry.counter("registration.inventory.compensated");
        this.consumeTimer = registry.timer("registration.message.consume.duration");
        Gauge.builder("registration.dead_letter.pending", deadLetterPending, AtomicLong::doubleValue)
                .description("待自动处理或正在自动重放的报名死信数量")
                .register(registry);
        Gauge.builder("registration.dead_letter.manual_required",
                        deadLetterManualRequiredCount, AtomicLong::doubleValue)
                .description("需要人工确认的报名死信数量")
                .register(registry);
        Gauge.builder("registration.dead_letter.oldest_pending_seconds",
                        deadLetterOldestPendingSeconds, AtomicLong::doubleValue)
                .description("最老待处理报名死信的等待秒数")
                .register(registry);
    }

    public void accepted() { accepted.increment(); }
    public void rejected() { rejected.increment(); }
    public void sendFailed() { sendFailed.increment(); }
    public void consumed() { consumed.increment(); }
    public void retried() { retried.increment(); }
    public void deadLettered() { deadLettered.increment(); }
    public void deadLetterAutoReplayed() { deadLetterAutoReplayed.increment(); }
    public void deadLetterAutoRetryFailed() { deadLetterAutoRetryFailed.increment(); }
    public void deadLetterManualRequired() { deadLetterManualRequired.increment(); }
    public void compensated() { compensated.increment(); }
    public void updateDeadLetterBacklog(long pending, long manualRequired, long oldestPendingSeconds) {
        deadLetterPending.set(Math.max(0L, pending));
        deadLetterManualRequiredCount.set(Math.max(0L, manualRequired));
        deadLetterOldestPendingSeconds.set(Math.max(0L, oldestPendingSeconds));
    }
    public Timer.Sample startConsume() { return Timer.start(); }
    public void stopConsume(Timer.Sample sample) { sample.stop(consumeTimer); }
}
