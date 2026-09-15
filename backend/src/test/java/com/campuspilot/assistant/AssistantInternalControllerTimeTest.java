package com.campuspilot.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证模型返回日期或日期时间时均可解析。 */
class AssistantInternalControllerTimeTest {

    @Test
    void parsesDateOnlyBounds() {
        AssistantInternalController controller = new AssistantInternalController();
        LocalDateTime start = ReflectionTestUtils.invokeMethod(
                controller, "parseDateTime", "2026-09-15", false);
        LocalDateTime end = ReflectionTestUtils.invokeMethod(
                controller, "parseDateTime", "2026-09-20", true);
        assertEquals(LocalDateTime.of(2026, 9, 15, 0, 0, 0), start);
        assertEquals(LocalDateTime.of(2026, 9, 20, 23, 59, 59), end);
    }
}
