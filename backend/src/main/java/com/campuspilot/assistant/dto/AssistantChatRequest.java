package com.campuspilot.assistant.dto;

import lombok.Data;

/** 前端发起助手对话时提交的请求。 */
@Data
public class AssistantChatRequest {

    /** 浏览器生成的会话标识。 */
    private String threadId;

    /** 用户本轮输入。 */
    private String message;

    /** 用户当前所在页面，用于理解上下文。 */
    private String pagePath;
}
