package com.campuspilot.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campuspilot.dto.Result;
import com.campuspilot.entity.Activity;
import com.campuspilot.mapper.ActivityMapper;
import com.campuspilot.service.IActivityService;
import com.campuspilot.utils.CacheClient;
import com.campuspilot.utils.SystemConstants;
import com.campuspilot.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.campuspilot.utils.RedisConstants.CACHE_ACTIVITY_KEY;
import static com.campuspilot.utils.RedisConstants.CACHE_ACTIVITY_TTL;

/** 处理活动的创建、查询与更新。 */
@Slf4j
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /** 校验活动内容，填充服务端管理字段后保存。 */
    @Override
    @Transactional
    public Result create(Activity activity) {
        validateActivity(activity);
        activity.setOrganizerId(UserHolder.getUser().getId());
        activity.setActivityStatus("PUBLISHED");
        activity.setSold(0);
        if (!save(activity)) {
            return Result.fail("活动创建失败");
        }
        return Result.ok(activity.getId());
    }

    /** 根据 ID 查询活动，并使用空值缓存防止缓存穿透。 */
    @Override
    public Result queryById(Long id) {
        Activity activity = cacheClient.queryWithPassThrough(
                CACHE_ACTIVITY_KEY,
                id,
                Activity.class,
                this::getById,
                CACHE_ACTIVITY_TTL,
                TimeUnit.MINUTES);
        return activity == null ? Result.fail("活动不存在！") : Result.ok(activity);
    }

    /** 检查活动归属后更新可编辑字段，并在事务提交后失效相关缓存。 */
    @Override
    @Transactional
    public Result update(Activity activity) {
        Long id = activity == null ? null : activity.getId();
        if (id == null) {
            return Result.fail("活动ID不能为空");
        }
        validateActivity(activity);
        Activity old = getById(id);
        if (old == null) {
            return Result.fail("活动不存在");
        }
        if (!"ADMIN".equals(UserHolder.getUser().getRole())
                && !UserHolder.getUser().getId().equals(old.getOrganizerId())) {
            throw new SecurityException("只能修改自己创建的活动");
        }

        // 这些字段由服务端维护，禁止通过编辑接口覆盖。
        activity.setOrganizerId(null);
        activity.setActivityStatus(null);
        activity.setSold(null);
        if (!updateById(activity)) {
            return Result.fail("活动更新失败");
        }

        // 缓存失效延后到事务提交后执行：避免 Redis 异常导致业务更新回滚，
        // 也避免事务未提交时读请求把旧数据回填进缓存。
        evictCacheAfterCommit(id);
        return Result.ok();
    }

    /** 按分类分页查询活动。 */
    @Override
    public Result queryActivitiesByCategory(Integer typeId, Integer current) {
        if (typeId == null || current == null || current < 1) {
            return Result.fail("分类和页码参数错误");
        }
        Page<Activity> page = query().eq("type_id", typeId)
                .orderByAsc("start_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /** 报名成功时同步活动已报名数 +1。 */
    @Override
    public void incrementSold(Long activityId) {
        if (activityId == null) {
            return;
        }
        update().setSql("sold = IFNULL(sold, 0) + 1").eq("id", activityId).update();
    }

    /** 取消报名时同步活动已报名数 -1（最低为 0）。 */
    @Override
    public void decrementSold(Long activityId) {
        if (activityId == null) {
            return;
        }
        update().setSql("sold = GREATEST(IFNULL(sold, 0) - 1, 0)").eq("id", activityId).update();
    }

    /** 按报名记录重建全部活动的已报名数，用于启动时校准存量数据。 */
    @Override
    public int recomputeSold() {
        return getBaseMapper().recomputeSold();
    }

    /** 集中校验活动的必填项、长度、时间关系与坐标范围。 */
    private void validateActivity(Activity activity) {
        if (activity == null || StrUtil.isBlank(activity.getName()) || activity.getName().length() > 128) {
            throw new IllegalArgumentException("活动名称应为1到128个字符");
        }
        if (activity.getTypeId() == null) {
            throw new IllegalArgumentException("请选择活动分类");
        }
        if (StrUtil.isBlank(activity.getImages()) || activity.getImages().length() > 1024) {
            throw new IllegalArgumentException("请提供有效的活动图片");
        }
        if (StrUtil.isBlank(activity.getAddress()) || activity.getAddress().length() > 255) {
            throw new IllegalArgumentException("请提供有效的活动地址");
        }
        if (activity.getDescription() != null && activity.getDescription().length() > 5000) {
            throw new IllegalArgumentException("活动介绍不能超过5000个字符");
        }
        if (activity.getCapacity() != null && activity.getCapacity() <= 0) {
            throw new IllegalArgumentException("活动容量必须大于0");
        }
        if (activity.getStartTime() != null && activity.getEndTime() != null
                && !activity.getStartTime().isBefore(activity.getEndTime())) {
            throw new IllegalArgumentException("活动开始时间必须早于结束时间");
        }
        if (activity.getRegistrationDeadline() != null && activity.getEndTime() != null
                && activity.getRegistrationDeadline().isAfter(activity.getEndTime())) {
            throw new IllegalArgumentException("报名截止时间不能晚于活动结束时间");
        }
    }

    /** 事务提交后删除缓存；删除失败只记日志，不影响主流程。 */
    private void evictCacheAfterCommit(Long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                stringRedisTemplate.delete(CACHE_ACTIVITY_KEY + id);
            } catch (Exception e) {
                log.warn("活动缓存删除失败 id={}", id, e);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    stringRedisTemplate.delete(CACHE_ACTIVITY_KEY + id);
                } catch (Exception e) {
                    log.warn("活动缓存删除失败 id={}", id, e);
                }
            }
        });
    }
}
