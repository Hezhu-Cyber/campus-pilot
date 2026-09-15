package com.campuspilot.controller;

import com.campuspilot.dto.Result;
import com.campuspilot.service.IActivityRegistrationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/** 提供活动报名相关的 HTTP 接口。 */
@RestController
@RequestMapping("/activity-registration")
public class ActivityRegistrationController {
    @Resource
    private IActivityRegistrationService activityRegistrationService;

    // 普通报名入口：POST /activity-registration/{id}
    @PostMapping("/{id}")
    public Result register(@PathVariable("id") Long registrationPassId) {
        return activityRegistrationService.register(registrationPassId);
    }

    // 限量报名入口：POST /activity-registration/limited/{id}
    @PostMapping("limited/{id}")
    public Result registerForLimitedActivity(@PathVariable("id") Long registrationPassId)
    {
        return activityRegistrationService.registerForLimitedActivity(registrationPassId);
    }

    /** 返回异步报名流水状态，前端据此轮询最终结果。 */
    @GetMapping("/{id}/status")
    public Result queryStatus(@PathVariable("id") Long registrationId) {
        return activityRegistrationService.queryRegistrationStatus(registrationId);
    }

    /** 返回当前用户的全部报名记录。 */
    @GetMapping("/mine")
    public Result mine() {
        return activityRegistrationService.queryMyRegistrations();
    }

    /** 校验报名归属后，通过事务代理执行取消。 */
    @DeleteMapping("/{id}")
    public Result cancel(@PathVariable("id") Long registrationId) {
        return activityRegistrationService.cancel(registrationId);
    }
}
