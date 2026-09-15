package com.campuspilot.controller;

import com.campuspilot.dto.Result;
import com.campuspilot.registration.RegistrationDeadLetterAdminService;
import com.campuspilot.utils.RoleGuard;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/** 提供报名死信的管理接口（仅管理员）。 */
@RestController
@RequestMapping("/admin/registration")
public class AdminRegistrationController {

    @Resource
    private RoleGuard roleGuard;
    @Resource
    private RegistrationDeadLetterAdminService deadLetterAdminService;

    /** 分页查看报名业务死信。 */
    @GetMapping("/dead-letters")
    public Result deadLetters(@RequestParam(value = "current", defaultValue = "1") Integer current,
                              @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        roleGuard.requireAdmin();
        return deadLetterAdminService.listDeadLetters(current, pageSize);
    }

    /** 重放一条死信（重置流水并重新走事务消息）。 */
    @PostMapping("/dead-letters/{id}/replay")
    public Result replay(@PathVariable("id") Long id) {
        roleGuard.requireAdmin();
        return deadLetterAdminService.replay(id);
    }
}
