package com.campuspilot.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuspilot.dto.Result;
import com.campuspilot.entity.SupportTicket;
import com.campuspilot.mapper.SupportTicketMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 创建、查询和流转人工客服工单。 */
@Service
public class SupportTicketService {

    private static final List<String> ALLOWED_STATUS = Arrays.asList(
            "OPEN", "PROCESSING", "RESOLVED", "CLOSED");

    @Resource
    private SupportTicketMapper supportTicketMapper;

    /** 创建一条待处理工单。 */
    public Long create(Long userId, String threadId, String category, String subject, String content) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少用户");
        }
        if (StrUtil.isBlank(content) || content.length() > 2000) {
            throw new IllegalArgumentException("工单内容应为1到2000个字符");
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(userId);
        ticket.setThreadId(StrUtil.sub(StrUtil.nullToEmpty(threadId), 0, 128));
        ticket.setCategory(StrUtil.sub(StrUtil.blankToDefault(category, "OTHER"), 0, 32));
        ticket.setSubject(StrUtil.sub(StrUtil.blankToDefault(subject, "用户请求人工客服"), 0, 128));
        ticket.setContent(content);
        ticket.setStatus("OPEN");
        ticket.setCreateTime(LocalDateTime.now());
        ticket.setUpdateTime(LocalDateTime.now());
        supportTicketMapper.insert(ticket);
        return ticket.getId();
    }

    /** 返回当前用户自己的工单。 */
    public Result listMine(Long userId) {
        return Result.ok(supportTicketMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SupportTicket>()
                        .eq("user_id", userId)
                        .orderByDesc("create_time")
                        .last("LIMIT 50")));
    }

    /** 管理员按状态分页查询工单。 */
    public Result listForAdmin(Integer current, Integer pageSize, String status) {
        int pageNumber = current == null || current < 1 ? 1 : current;
        int size = pageSize == null || pageSize < 1 || pageSize > 100 ? 20 : pageSize;
        Page<SupportTicket> page = new Page<>(pageNumber, size);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SupportTicket> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("create_time");
        Page<SupportTicket> result = supportTicketMapper.selectPage(page, wrapper);
        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("current", result.getCurrent());
        response.put("pages", result.getPages());
        return Result.ok(response);
    }

    /** 更新工单处理状态。 */
    public Result updateStatus(Long id, String status, Long handlerId) {
        if (id == null || !ALLOWED_STATUS.contains(status)) {
            return Result.fail("工单状态参数错误");
        }
        SupportTicket existing = supportTicketMapper.selectById(id);
        if (existing == null) {
            return Result.fail("工单不存在");
        }
        SupportTicket update = new SupportTicket();
        update.setId(id);
        update.setStatus(status);
        update.setHandlerId(handlerId);
        update.setUpdateTime(LocalDateTime.now());
        supportTicketMapper.updateById(update);
        return Result.ok();
    }
}
