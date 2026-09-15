package com.campuspilot.assistant;

import com.campuspilot.dto.Result;
import com.campuspilot.assistant.dto.AssistantChatRequest;

/** 智能助手业务契约。 */
public interface IAssistantService {

    /** 执行一次智能助手对话。 */
    Result chat(AssistantChatRequest request);
}
