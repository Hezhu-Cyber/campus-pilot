package com.campuspilot.controller;

import com.campuspilot.dto.Result;
import com.campuspilot.dto.SupportTicketStatusRequest;
import com.campuspilot.support.SupportTicketService;
import com.campuspilot.utils.RoleGuard;
import com.campuspilot.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/** 用户客服记录和管理员工单处理接口。 */
@RestController
public class SupportTicketController {

    @Resource
    private SupportTicketService supportTicketService;

    @Resource
    private RoleGuard roleGuard;

    @GetMapping("/support-tickets/mine")
    public Result mine() {
        return supportTicketService.listMine(UserHolder.getUser().getId());
    }

    @GetMapping("/admin/support-tickets")
    public Result adminList(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "status", required = false) String status) {
        roleGuard.requireAdmin();
        return supportTicketService.listForAdmin(current, pageSize, status);
    }

    @PutMapping("/admin/support-tickets/{id}/status")
    public Result updateStatus(
            @PathVariable("id") Long id,
            @RequestBody SupportTicketStatusRequest request) {
        roleGuard.requireAdmin();
        return supportTicketService.updateStatus(
                id,
                request == null ? null : request.getStatus(),
                UserHolder.getUser().getId());
    }
}
