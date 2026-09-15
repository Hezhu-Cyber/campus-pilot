package com.campuspilot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campuspilot.dto.Result;
import com.campuspilot.entity.RegistrationPass;
import com.campuspilot.mapper.RegistrationPassMapper;
import com.campuspilot.entity.LimitedRegistrationQuota;
import com.campuspilot.registration.RegistrationReservationService;
import com.campuspilot.service.ILimitedRegistrationQuotaService;
import com.campuspilot.service.IRegistrationPassService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/** 实现报名凭证的业务规则与持久化协调。 */
@Service
public class RegistrationPassServiceImpl extends ServiceImpl<RegistrationPassMapper, RegistrationPass> implements IRegistrationPassService {

    @Resource
    private ILimitedRegistrationQuotaService registerForLimitedActivityService;
    @Resource
    private RegistrationReservationService reservationService;

    /** 查询活动下的报名凭证。 */
    @Override
    public Result queryPassesByActivity(Long activityId) {

        List<RegistrationPass> registrationPasses = getBaseMapper().queryPassesByActivity(activityId);

        return Result.ok(registrationPasses);
    }

    /** 在事务中保存限量凭证、配额并预热 Redis 库存。 */
    @Override
    @Transactional
    public void addLimitedRegistrationQuota(RegistrationPass registrationPass) {
        if (registrationPass == null || registrationPass.getActivityId() == null) {
            throw new IllegalArgumentException("请选择关联活动");
        }
        if (registrationPass.getStock() == null || registrationPass.getStock() <= 0) {
            throw new IllegalArgumentException("限量报名名额必须大于0");
        }
        if (registrationPass.getBeginTime() == null || registrationPass.getEndTime() == null
                || !registrationPass.getBeginTime().isBefore(registrationPass.getEndTime())) {
            throw new IllegalArgumentException("报名开始时间必须早于结束时间");
        }
        registrationPass.setType(1);
        registrationPass.setStatus(1);

        save(registrationPass);

        LimitedRegistrationQuota registerForLimitedActivity = new LimitedRegistrationQuota();
        registerForLimitedActivity.setRegistrationPassId(registrationPass.getId());
        registerForLimitedActivity.setStock(registrationPass.getStock());
        registerForLimitedActivity.setBeginTime(registrationPass.getBeginTime());
        registerForLimitedActivity.setEndTime(registrationPass.getEndTime());
        registerForLimitedActivityService.save(registerForLimitedActivity);

        reservationService.initializeIfAbsent(registerForLimitedActivity);
    }
}
