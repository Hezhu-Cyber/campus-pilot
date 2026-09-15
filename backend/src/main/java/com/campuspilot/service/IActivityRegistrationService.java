package com.campuspilot.service;

import com.campuspilot.dto.Result;
import com.campuspilot.entity.ActivityRegistration;
import com.baomidou.mybatisplus.extension.service.IService;

/** 定义活动报名的业务服务契约。 */
public interface IActivityRegistrationService extends IService<ActivityRegistration> {

    /** 根据凭证类型选择普通或限量报名流程。 */
    Result register(Long registrationPassId);

    /** 通过 Lua 原子预扣 Redis 库存后创建限量报名。 */
    Result registerForLimitedActivity(Long registrationPassId);

    /** 查询 RocketMQ 异步报名流水的最终处理状态。 */
    Result queryRegistrationStatus(Long registrationId);

    /** 在数据库事务中创建报名记录并同步数量。 */
    void createActivityRegistration(ActivityRegistration registrationPassId);

    /** 查询当前用户的报名记录并组装展示数据。 */
    Result queryMyRegistrations();

    /** 校验报名归属后，通过事务代理执行取消。 */
    Result cancel(Long registrationId);

    /** 在事务中取消报名，并必要时回补限量库存。 */
    void cancelRegistration(ActivityRegistration registration);
}
