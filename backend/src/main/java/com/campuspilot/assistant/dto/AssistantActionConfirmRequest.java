package com.campuspilot.assistant.dto;

import lombok.Data;

/** 前端确认助手操作时提交的一次性令牌。 */
@Data
public class AssistantActionConfirmRequest {

    private String token;
}
