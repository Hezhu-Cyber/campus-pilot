package com.campuspilot.config;

import com.campuspilot.entity.ActivityRegistration;
import com.campuspilot.entity.LimitedRegistrationQuota;
import com.campuspilot.service.IActivityRegistrationService;
import com.campuspilot.service.IActivityService;
import com.campuspilot.service.ILimitedRegistrationQuotaService;
import com.campuspilot.registration.RegistrationReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.util.List;

/** 启动时校准活动报名数，并安全地初始化限量报名的 Redis 派生数据。 */
@Slf4j
@Component
public class StartupDataInitializer implements ApplicationRunner {
    @Value("${app.initialize-redis:true}")
    private boolean enabled;

    @Resource private ILimitedRegistrationQuotaService quotaService;
    @Resource private IActivityRegistrationService registrationService;
    @Resource private IActivityService activityService;
    @Resource private StringRedisTemplate redis;

    @Override
    public void run(ApplicationArguments args) {
        // 报名数属于展示数据，始终按报名记录重算一次，校准存量脏数据。
        try {
            int updated = activityService.recomputeSold();
            log.info("activity sold recomputed updatedCount={}", updated);
        } catch (Exception e) {
            log.warn("活动已报名数重算失败，应用仍可启动；请检查 MySQL 连接", e);
        }
        if (!enabled) return;
        try {
            initializeRegistrationState();
        } catch (Exception e) {
            log.warn("Redis 业务数据初始化失败，应用仍可启动；请检查 MySQL/Redis 和迁移脚本", e);
        }
    }

    /**
     * 安全地初始化限量报名的 Redis 库存与已报名用户集合。
     * 库存键只在缺失时写入（setIfAbsent）：绝不能用数据库值覆盖运行中的预占，
     * 否则重启会把"已预占未落库"的名额重新放出导致超卖。
     * 用户集合只在缺失时按数据库已提交的报名重建，避免重复预占。
     */
    private void initializeRegistrationState() {
        for (LimitedRegistrationQuota quota : quotaService.list()) {
            Long passId = quota.getRegistrationPassId();
            redis.opsForValue().setIfAbsent(RegistrationReservationService.stockKey(passId), quota.getStock().toString());
            if (quota.getBeginTime() != null) {
                redis.opsForValue().set(RegistrationReservationService.beginKey(passId),
                        String.valueOf(quota.getBeginTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
            }
            if (quota.getEndTime() != null) {
                redis.opsForValue().set(RegistrationReservationService.endKey(passId),
                        String.valueOf(quota.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
            }
            redis.opsForValue().set(RegistrationReservationService.enabledKey(passId), "1");
            String usersKey = RegistrationReservationService.usersKey(passId);
            if (Boolean.TRUE.equals(redis.hasKey(usersKey))) {
                continue;
            }
            List<ActivityRegistration> active = registrationService.query()
                    .eq("registration_pass_id", passId).ne("status", 4).list();
            for (ActivityRegistration registration : active) {
                redis.opsForSet().add(usersKey, registration.getUserId().toString());
            }
        }
    }
}
