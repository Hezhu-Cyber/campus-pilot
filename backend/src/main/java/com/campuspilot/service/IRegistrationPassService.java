package com.campuspilot.service;

import com.campuspilot.dto.Result;
import com.campuspilot.entity.RegistrationPass;
import com.baomidou.mybatisplus.extension.service.IService;

/** 定义报名凭证的业务服务契约。 */
public interface IRegistrationPassService extends IService<RegistrationPass> {

    /** 查询活动下的报名凭证。 */
    Result queryPassesByActivity(Long activityId);

    /** 在事务中保存限量凭证、配额并预热 Redis 库存。 */
    void addLimitedRegistrationQuota(RegistrationPass registrationPass);
}
