package com.campuspilot.service;

import com.campuspilot.dto.Result;
import com.campuspilot.entity.Activity;
import com.baomidou.mybatisplus.extension.service.IService;

/** 定义活动的业务服务契约。 */
public interface IActivityService extends IService<Activity> {

    /** 执行 create 对应的业务逻辑。 */
    Result create(Activity activity);

    /** 执行 queryById 对应的业务逻辑。 */
    Result queryById(Long id);

    /** 执行 update 对应的业务逻辑。 */
    Result update(Activity activity);

    /** 按分类分页查询活动。 */
    Result queryActivitiesByCategory(Integer typeId, Integer current);

    /** 报名成功时同步活动已报名数 +1。 */
    void incrementSold(Long activityId);

    /** 取消报名时同步活动已报名数 -1（最低为 0）。 */
    void decrementSold(Long activityId);

    /** 按报名记录重建全部活动的已报名数，用于启动时校准存量数据。 */
    int recomputeSold();
}
