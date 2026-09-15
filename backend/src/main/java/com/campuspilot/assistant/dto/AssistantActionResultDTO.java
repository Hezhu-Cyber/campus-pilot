package com.campuspilot.assistant.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 助手操作执行后的可查询结果。 */
@Data
@NoArgsConstructor
public class AssistantActionResultDTO {

    private String actionId;
    private String actionType;
    private String status;
    private String message;
    private Long registrationId;
    private boolean requiresPolling;
    private boolean retryable;

    public static AssistantActionResultDTO terminal(
            String actionId, String actionType, String status, String message, Long registrationId) {
        AssistantActionResultDTO result = new AssistantActionResultDTO();
        result.actionId = actionId;
        result.actionType = actionType;
        result.status = status;
        result.message = message;
        result.registrationId = registrationId;
        result.requiresPolling = false;
        result.retryable = false;
        return result;
    }

    public static AssistantActionResultDTO processing(
            String actionId, String actionType, String message, Long registrationId) {
        AssistantActionResultDTO result = new AssistantActionResultDTO();
        result.actionId = actionId;
        result.actionType = actionType;
        result.status = "PROCESSING";
        result.message = message;
        result.registrationId = registrationId;
        result.requiresPolling = true;
        result.retryable = false;
        return result;
    }
}
