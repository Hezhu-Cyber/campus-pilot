package com.campuspilot.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Redis 中保存的精简会话消息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantConversationMessageDTO {
    private String role;
    private String content;
}
