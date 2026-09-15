package com.campuspilot.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redisPingHealthIndicator")
public class RedisPingHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;

    public RedisPingHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            if ("PONG".equalsIgnoreCase(response)) {
                return Health.up().withDetail("ping", response).build();
            }
            return Health.down().withDetail("ping", response).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
