package com.campuspilot.assistant.dto;

import lombok.Data;

/** Python 请求 Java 创建客服工单。 */
@Data
public class AssistantSupportTicketRequest {
    private String threadId;
    private String category;
    private String subject;
    private String content;
}
