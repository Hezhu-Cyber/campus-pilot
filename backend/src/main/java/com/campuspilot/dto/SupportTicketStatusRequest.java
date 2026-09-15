package com.campuspilot.dto;

import lombok.Data;

/** 管理员更新客服工单状态。 */
@Data
public class SupportTicketStatusRequest {
    private String status;
}
