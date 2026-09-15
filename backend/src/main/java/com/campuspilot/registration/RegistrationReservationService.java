package com.campuspilot.registration;

import com.campuspilot.entity.LimitedRegistrationQuota;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RegistrationReservationService {
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT;
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        RESERVE_SCRIPT = new DefaultRedisScript<>();
        RESERVE_SCRIPT.setLocation(new ClassPathResource("registration_reserve.lua"));
        RESERVE_SCRIPT.setResultType(Long.class);
        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setLocation(new ClassPathResource("registration_compensate.lua"));
        COMPENSATE_SCRIPT.setResultType(Long.class);
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("rollback_registration.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final RegistrationMqProperties properties;

    public RegistrationReserveResult reserve(RegistrationMessage message) {
        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                Arrays.asList(
                        stockKey(message.getRegistrationPassId()),
                        usersKey(message.getRegistrationPassId()),
                        beginKey(message.getRegistrationPassId()),
                        endKey(message.getRegistrationPassId()),
                        enabledKey(message.getRegistrationPassId()),
                        transactionKey(message.getRegistrationPassId(), message.getRegistrationId())
                ),
                message.getUserId().toString(),
                message.getRequestTime().toString(),
                String.valueOf(properties.getTransactionStateTtlSeconds())
        );
        if (result == null) {
            throw new IllegalStateException("Redis did not return a registration reservation result");
        }
        return RegistrationReserveResult.fromCode(result);
    }

    public boolean compensate(RegistrationMessage message) {
        Long result = redisTemplate.execute(
                COMPENSATE_SCRIPT,
                Arrays.asList(
                        stockKey(message.getRegistrationPassId()),
                        usersKey(message.getRegistrationPassId()),
                        transactionKey(message.getRegistrationPassId(), message.getRegistrationId())
                ),
                message.getUserId().toString(),
                String.valueOf(properties.getTransactionStateTtlSeconds())
        );
        return result != null && result == 0L;
    }

    /**
     * 用户取消报名时回补 Redis 预占：移除预留集合成员、回补库存并清除事务状态。
     * 脚本幂等：重复执行不会重复回补库存。
     */
    public boolean rollback(Long registrationPassId, Long userId, Long registrationId) {
        Long result = redisTemplate.execute(
                ROLLBACK_SCRIPT,
                Collections.emptyList(),
                registrationPassId.toString(),
                userId.toString(),
                registrationId.toString()
        );
        return result != null;
    }

    public String transactionState(Long registrationPassId, Long registrationId) {
        return redisTemplate.opsForValue().get(transactionKey(registrationPassId, registrationId));
    }

    public void markProcessing(RegistrationMessage message) {
        setTransactionState(message, RegistrationStatus.PROCESSING);
    }

    public void markSuccess(RegistrationMessage message) {
        setTransactionState(message, RegistrationStatus.SUCCESS);
    }

    public void initializeIfAbsent(LimitedRegistrationQuota quota) {
        String passId = quota.getRegistrationPassId().toString();
        redisTemplate.opsForValue().setIfAbsent(stockKey(passId), quota.getStock().toString());
        if (quota.getBeginTime() != null) {
            redisTemplate.opsForValue().set(beginKey(passId),
                    String.valueOf(quota.getBeginTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        }
        if (quota.getEndTime() != null) {
            redisTemplate.opsForValue().set(endKey(passId),
                    String.valueOf(quota.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        }
        redisTemplate.opsForValue().set(enabledKey(passId), "1");
    }

    private void setTransactionState(RegistrationMessage message, RegistrationStatus status) {
        redisTemplate.opsForValue().set(
                transactionKey(message.getRegistrationPassId(), message.getRegistrationId()),
                status.name(), properties.getTransactionStateTtlSeconds(), TimeUnit.SECONDS);
    }

    public static String stockKey(Long passId) { return stockKey(passId.toString()); }
    public static String usersKey(Long passId) { return "registration:{" + passId + "}:users"; }
    public static String beginKey(Long passId) { return beginKey(passId.toString()); }
    public static String endKey(Long passId) { return endKey(passId.toString()); }
    public static String enabledKey(Long passId) { return enabledKey(passId.toString()); }
    public static String transactionKey(Long passId, Long registrationId) {
        return "registration:{" + passId + "}:tx:" + registrationId;
    }
    private static String stockKey(String passId) { return "registration:{" + passId + "}:stock"; }
    private static String beginKey(String passId) { return "registration:{" + passId + "}:begin"; }
    private static String endKey(String passId) { return "registration:{" + passId + "}:end"; }
    private static String enabledKey(String passId) { return "registration:{" + passId + "}:enabled"; }
}
