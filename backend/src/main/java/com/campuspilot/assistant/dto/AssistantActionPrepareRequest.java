package com.campuspilot.assistant.dto;

import lombok.Data;

/** Python 请求 Spring 准备一次确认式操作。 */
@Data
public class AssistantActionPrepareRequest {

    private Long activityId;
    private Long registrationPassId;
    private Long registrationId;
}
