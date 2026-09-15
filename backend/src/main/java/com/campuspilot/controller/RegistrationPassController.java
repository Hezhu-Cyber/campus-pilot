package com.campuspilot.controller;

import com.campuspilot.dto.Result;
import com.campuspilot.entity.RegistrationPass;
import com.campuspilot.service.IRegistrationPassService;
import com.campuspilot.service.IActivityService;
import com.campuspilot.entity.Activity;
import com.campuspilot.utils.RoleGuard;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/** 提供报名凭证相关的 HTTP 接口。 */
@RestController
@RequestMapping("/registration-pass")
public class RegistrationPassController {

    @Resource
    private IRegistrationPassService registrationPassService;
    @Resource
    private RoleGuard roleGuard;
    @Resource
    private IActivityService activityService;

    /** 为当前组织者的活动创建普通报名凭证。 */
    @PostMapping
    public Result addRegistrationPass(@RequestBody RegistrationPass registrationPass) {
        roleGuard.requireOrganizer();
        if (registrationPass == null || registrationPass.getActivityId() == null
                || registrationPass.getTitle() == null || registrationPass.getTitle().trim().isEmpty()) {
            return Result.fail("报名标题和关联活动不能为空");
        }
        Result ownership = requireOwnedActivity(registrationPass.getActivityId());
        if (ownership != null) return ownership;
        registrationPass.setType(0);
        registrationPass.setStatus(1);
        registrationPassService.save(registrationPass);
        return Result.ok(registrationPass.getId());
    }

    /** 在事务中保存限量凭证、配额并预热 Redis 库存。 */
    @PostMapping("limited")
    public Result addLimitedRegistrationQuota(@RequestBody RegistrationPass registrationPass) {
        roleGuard.requireOrganizer();
        if (registrationPass == null || registrationPass.getActivityId() == null) {
            return Result.fail("请选择关联活动");
        }
        Result ownership = requireOwnedActivity(registrationPass.getActivityId());
        if (ownership != null) return ownership;
        registrationPassService.addLimitedRegistrationQuota(registrationPass);
        return Result.ok(registrationPass.getId());
    }

    /** 查询活动下的报名凭证。 */
    @GetMapping("/activity/{activityId}")
    public Result queryPassesByActivity(@PathVariable("activityId") Long activityId) {
       return registrationPassService.queryPassesByActivity(activityId);
    }

    /** 确认活动存在，且当前用户具有管理权。 */
    private Result requireOwnedActivity(Long activityId) {
        Activity activity = activityService.getById(activityId);
        if (activity == null) return Result.fail("关联活动不存在");
        roleGuard.requireOwnerOrAdmin(activity.getOrganizerId());
        return null;
    }
}
