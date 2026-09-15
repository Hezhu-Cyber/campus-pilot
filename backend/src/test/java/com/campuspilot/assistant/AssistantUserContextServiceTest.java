package com.campuspilot.assistant;

import com.campuspilot.dto.UserDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 Java 到 Python 的短期用户上下文签名。 */
class AssistantUserContextServiceTest {

    @Test
    void issuesAndVerifiesSignedContext() {
        AssistantProperties properties = new AssistantProperties();
        properties.setUserContextSecret("0123456789abcdef0123456789abcdef");
        properties.setUserContextTtlSeconds(60);

        AssistantUserContextService service = new AssistantUserContextService();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        UserDTO user = new UserDTO();
        user.setId(42L);
        user.setRole("STUDENT");
        String token = service.issue(user);
        UserDTO verified = service.verify(token);

        assertEquals(42L, verified.getId());
        assertEquals("STUDENT", verified.getRole());
        assertThrows(IllegalArgumentException.class, () -> service.verify(token + "tampered"));
    }
}
