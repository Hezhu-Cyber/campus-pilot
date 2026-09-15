package com.campuspilot.assistant.dto;

import com.campuspilot.dto.RegistrationViewDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Python 助手服务返回的结构化回答。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantChatResponse {

    /** 会话标识。 */
    private String threadId;

    /** 面向用户的中文回答。 */
    private String answer;

    /** 推荐或命中的活动。 */
    private List<AssistantActivityDTO> activities = new ArrayList<>();

    /** 当前用户报名记录。 */
    private List<RegistrationViewDTO> registrations = new ArrayList<>();

    /** 热门动态。 */
    private List<AssistantPostDTO> posts = new ArrayList<>();

    /** 推荐的后续问题。 */
    private List<String> suggestedQuestions = new ArrayList<>();

    /** 需要用户确认的操作。 */
    private AssistantPendingActionDTO pendingAction;

    /** 本次回答来自模型还是规则降级。 */
    private String mode;
}

