package com.campuspilot.registration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuspilot.entity.ActivityRegistration;
import com.campuspilot.entity.RegistrationPass;
import com.campuspilot.entity.RegistrationRequest;
import com.campuspilot.mapper.ActivityRegistrationMapper;
import com.campuspilot.mapper.LimitedRegistrationQuotaMapper;
import com.campuspilot.service.IActivityService;
import com.campuspilot.service.IRegistrationPassService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationPersistenceService {
    private final RegistrationRequestRepository requestRepository;
    private final ActivityRegistrationMapper registrationMapper;
    private final LimitedRegistrationQuotaMapper quotaMapper;
    private final IRegistrationPassService passService;
    private final IActivityService activityService;

    @Transactional(rollbackFor = Exception.class)
    public void persist(RegistrationMessage message) {
        // ① 流水账还在吗？不在 → 永久失败
        RegistrationRequest request = requestRepository.findById(message.getRegistrationId());
        if (request == null) {
            throw new RegistrationPermanentException("REQUEST_NOT_FOUND", "报名流水不存在");
        }
        // ② 已经是成功 → 直接返回（幂等：重复投递不会重复扣）
        if (RegistrationStatus.SUCCESS.name().equals(request.getStatus())) {
            return;
        }
        // ③ 已经失败/已关闭 → 永久失败
        if (RegistrationStatus.FAILED.name().equals(request.getStatus()) ||
                RegistrationStatus.COMPENSATED.name().equals(request.getStatus())) {
            throw new RegistrationPermanentException("REQUEST_CLOSED", "报名流水已关闭");
        }

        ActivityRegistration existing = registrationMapper.selectOne(new LambdaQueryWrapper<ActivityRegistration>()
                .eq(ActivityRegistration::getUserId, message.getUserId())
                .eq(ActivityRegistration::getRegistrationPassId, message.getRegistrationPassId())
                .last("LIMIT 1"));
        // ④ 这个人是不是已经报过（且没取消）→ 是就直接标记成功（还是幂等）
        if (existing != null && !Integer.valueOf(4).equals(existing.getStatus())) {
            requestRepository.updateStatus(message.getRegistrationId(), RegistrationStatus.SUCCESS, null, null);
            return;
        }
        // ⑤ 数据库名额减 1（第 3 层保险）
        requestRepository.updateStatus(message.getRegistrationId(), RegistrationStatus.PROCESSING, null, null);
        if (quotaMapper.decrementStock(message.getRegistrationPassId()) == 0) {
            throw new RegistrationPermanentException("DATABASE_STOCK_INSUFFICIENT", "数据库活动名额不足");
        }

        boolean wrote = false;
        if (existing != null) {
            existing.setStatus(2);
            existing.setPayType(1);
            registrationMapper.updateById(existing);
            wrote = true;
        } else {
            ActivityRegistration registration = new ActivityRegistration();
            registration.setId(message.getRegistrationId());
            registration.setUserId(message.getUserId());
            registration.setRegistrationPassId(message.getRegistrationPassId());
            registration.setStatus(2);
            registration.setPayType(1);
            registrationMapper.insert(registration);
            wrote = true;
        }
        // ⑦ 流水标记成功，活动"已报名人数"+1
        requestRepository.updateStatus(message.getRegistrationId(), RegistrationStatus.SUCCESS, null, null);
        if (wrote) {
            incrementActivitySold(message.getRegistrationPassId());
        }
    }

    /** 报名落库成功后同步活动页"已报名"人数。 */
    private void incrementActivitySold(Long registrationPassId) {
        try {
            RegistrationPass pass = passService.getById(registrationPassId);
            if (pass != null) {
                activityService.incrementSold(pass.getActivityId());
            }
        } catch (Exception e) {
            // 计数是展示数据，失败不能影响报名主流程，记日志由启动重算兜底。
            log.warn("increment activity sold failed passId={}", registrationPassId, e);
        }
    }

    public boolean registrationExists(RegistrationMessage message) {
        Integer count = registrationMapper.selectCount(new LambdaQueryWrapper<ActivityRegistration>()
                .eq(ActivityRegistration::getUserId, message.getUserId())
                .eq(ActivityRegistration::getRegistrationPassId, message.getRegistrationPassId())
                .ne(ActivityRegistration::getStatus, 4));
        return count != null && count > 0;
    }
}
