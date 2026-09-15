package com.campuspilot.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证生产密钥缺失或为示例值时拒绝启动。 */
class AssistantPropertiesTest {

    @Test
    void rejectsDefaultSecret() {
        AssistantProperties properties = new AssistantProperties();
        properties.setEnabled(true);
        properties.setInternalToken("campus-pilot-local-assistant-token");
        properties.setUserContextSecret("0123456789abcdef0123456789abcdef");
        assertThrows(IllegalStateException.class, properties::validateRuntime);
    }

    @Test
    void acceptsIndependentStrongSecrets() {
        AssistantProperties properties = new AssistantProperties();
        properties.setEnabled(true);
        properties.setInternalToken("0123456789abcdef0123456789abcdef");
        properties.setUserContextSecret("abcdef0123456789abcdef0123456789");
        assertDoesNotThrow(properties::validateRuntime);
    }
}
