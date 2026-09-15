package com.campuspilot.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.ActivityUpsertDTO;
import com.campuspilot.entity.Activity;
import com.campuspilot.service.IActivityService;
import com.campuspilot.utils.SystemConstants;
import com.campuspilot.utils.RoleGuard;
import com.campuspilot.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/** 提供活动相关的 HTTP 接口。 */
@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Resource
    public IActivityService activityService;

    @Resource
    private RoleGuard roleGuard;

    /** 返回当前组织者创建的活动；管理员进入组织者工作台时返回自己的活动。 */
    @GetMapping("/mine")
    public Result queryMyActivities() {
        roleGuard.requireOrganizer();
        return Result.ok(activityService.query()
                .eq("organizer_id", UserHolder.getUser().getId())
                .orderByDesc("create_time")
                .list());
    }

    /** 根据 ID 返回活动详情。 */
    @GetMapping("/{id}")
    public Result queryActivityById(@PathVariable("id") Long id) {
        return activityService.queryById(id);
    }

    /** 校验组织者权限并创建活动。 */
    @PostMapping
    public Result saveActivity(@RequestBody ActivityUpsertDTO request) {
        roleGuard.requireOrganizer();
        return activityService.create(toActivity(request, false));
    }

    /** 校验组织者权限并更新活动。 */
    @PutMapping
    public Result updateActivity(@RequestBody ActivityUpsertDTO request) {
        roleGuard.requireOrganizer();
        return activityService.update(toActivity(request, true));
    }

    /** 按分类分页查询活动。 */
    @GetMapping("/by-category")
    public Result queryActivitiesByCategory(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return activityService.queryActivitiesByCategory(typeId, current);
    }

    /** 按名称关键字分页搜索活动。 */
    @GetMapping("/search")
    public Result queryActivityByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        if (current == null || current < 1) return Result.fail("页码参数错误");
        Page<Activity> page = activityService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /** 将白名单请求 DTO 转换为持久化实体。 */
    private Activity toActivity(ActivityUpsertDTO request, boolean includeId) {
        if (request == null) throw new IllegalArgumentException("活动参数不能为空");
        Activity activity = new Activity();
        if (includeId) activity.setId(request.getId());
        activity.setName(request.getName());
        activity.setTypeId(request.getTypeId());
        activity.setDescription(request.getDescription());
        activity.setImages(request.getImages());
        activity.setArea(request.getArea());
        activity.setAddress(request.getAddress());
        activity.setAvgPrice(request.getAvgPrice());
        activity.setOpenHours(request.getOpenHours());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setRegistrationDeadline(request.getRegistrationDeadline());
        activity.setCapacity(request.getCapacity());
        return activity;
    }
}
