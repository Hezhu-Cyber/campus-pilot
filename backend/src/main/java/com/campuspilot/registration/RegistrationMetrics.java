package com.campuspilot.registration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class RegistrationMetrics {
    private final Counter accepted;
    private final Counter rejected;
    private final Counter sendFailed;
    private final Counter consumed;
    private final Counter retried;
    private final Counter deadLettered;
    private final Counter compensated;
    private final Timer consumeTimer;

    public RegistrationMetrics(MeterRegistry registry) {
        this.accepted = registry.counter("registration.request.accepted");
        this.rejected = registry.counter("registration.request.rejected");
        this.sendFailed = registry.counter("registration.message.send.failed");
        this.consumed = registry.counter("registration.message.consume.success");
        this.retried = registry.counter("registration.message.consume.retry");
        this.deadLettered = registry.counter("registration.message.dead_letter");
        this.compensated = registry.counter("registration.inventory.compensated");
        this.consumeTimer = registry.timer("registration.message.consume.duration");
    }

    public void accepted() { accepted.increment(); }
    public void rejected() { rejected.increment(); }
    public void sendFailed() { sendFailed.increment(); }
    public void consumed() { consumed.increment(); }
    public void retried() { retried.increment(); }
    public void deadLettered() { deadLettered.increment(); }
    public void compensated() { compensated.increment(); }
    public Timer.Sample startConsume() { return Timer.start(); }
    public void stopConsume(Timer.Sample sample) { sample.stop(consumeTimer); }
}
