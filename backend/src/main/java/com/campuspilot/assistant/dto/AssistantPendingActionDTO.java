package com.campuspilot.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 前端待用户确认的助手操作。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantPendingActionDTO {

    private String actionId;
    private String confirmationToken;
    private String actionType;
    private Long activityId;
    private Long registrationId;
    private Long registrationPassId;
    private String activityName;
    private String passTitle;
    private String summary;
    private Long expiresInSeconds;
    private Long expiresAt;
}
