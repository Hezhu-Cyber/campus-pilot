package com.campuspilot.assistant;

import com.campuspilot.assistant.dto.AssistantActionResultDTO;
import com.campuspilot.assistant.dto.AssistantPendingActionDTO;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.entity.Activity;
import com.campuspilot.entity.RegistrationPass;
import com.campuspilot.service.IActivityRegistrationService;
import com.campuspilot.service.IActivityService;
import com.campuspilot.service.IRegistrationPassService;
import com.campuspilot.utils.UserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证确认动作的状态机和幂等性。 */
class AssistantActionServiceTest {

    private final Map<String, String> redis = new HashMap<>();

    @AfterEach
    void cleanup() {
        UserHolder.removeUser();
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedConfirmationExecutesOnce() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AssistantActionService service = new AssistantActionService();

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        RLock lock = mock(RLock.class);
        when(lock.tryLock(0L, 30L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedissonClient redisson = mock(RedissonClient.class);
        when(redisson.getLock(anyString())).thenReturn(lock);

        IActivityRegistrationService registrationService = mock(IActivityRegistrationService.class);
        when(registrationService.register(20L)).thenReturn(Result.ok(123L));
        IRegistrationPassService passService = mock(IRegistrationPassService.class);
        RegistrationPass pass = new RegistrationPass();
        pass.setId(20L);
        pass.setActivityId(2L);
        pass.setStatus(1);
        pass.setType(0);
        when(passService.getById(20L)).thenReturn(pass);
        IActivityService activityService = mock(IActivityService.class);
        Activity activity = new Activity();
        activity.setId(2L);
        activity.setActivityStatus("PUBLISHED");
        when(activityService.getById(2L)).thenReturn(activity);

        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "redissonClient", redisson);
        ReflectionTestUtils.setField(service, "registrationService", registrationService);
        ReflectionTestUtils.setField(service, "registrationPassService", passService);
        ReflectionTestUtils.setField(service, "activityService", activityService);

        UserDTO user = new UserDTO();
        user.setId(7L);
        user.setRole("STUDENT");
        UserHolder.saveUser(user);

        AssistantPendingActionDTO action = new AssistantPendingActionDTO();
        action.setActionId("action-1");
        action.setConfirmationToken("action-1");
        action.setActionType("register");
        action.setRegistrationPassId(20L);
        action.setExpiresAt(System.currentTimeMillis() + 60_000L);
        AssistantActionService.StoredAction stored = new AssistantActionService.StoredAction();
        stored.userId = 7L;
        stored.status = "PENDING";
        stored.createdAt = System.currentTimeMillis();
        stored.updatedAt = stored.createdAt;
        stored.action = action;
        redis.put("assistant:action:7:action-1", objectMapper.writeValueAsString(stored));

        Result first = service.confirm("action-1");
        Result second = service.confirm("action-1");

        assertTrue(first.getSuccess());
        assertTrue(second.getSuccess());
        AssistantActionResultDTO firstResult = (AssistantActionResultDTO) first.getData();
        AssistantActionResultDTO secondResult = (AssistantActionResultDTO) second.getData();
        assertEquals("SUCCESS", firstResult.getStatus());
        assertEquals("SUCCESS", secondResult.getStatus());
        verify(registrationService, times(1)).register(20L);
    }
}
