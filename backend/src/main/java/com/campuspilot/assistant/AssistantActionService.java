package com.campuspilot.assistant;

import cn.hutool.core.util.StrUtil;
import com.campuspilot.assistant.dto.AssistantActionResultDTO;
import com.campuspilot.assistant.dto.AssistantPendingActionDTO;
import com.campuspilot.dto.RegistrationStatusDTO;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.entity.Activity;
import com.campuspilot.entity.ActivityRegistration;
import com.campuspilot.entity.LimitedRegistrationQuota;
import com.campuspilot.entity.RegistrationPass;
import com.campuspilot.service.IActivityRegistrationService;
import com.campuspilot.service.IActivityService;
import com.campuspilot.service.ILimitedRegistrationQuotaService;
import com.campuspilot.service.IRegistrationPassService;
import com.campuspilot.utils.UserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** 管理可过期、可查询、可安全重试的助手确认动作。 */
@Slf4j
@Service
public class AssistantActionService {

    private static final String ACTION_KEY_PREFIX = "assistant:action:";
    private static final String ACTION_LOCK_PREFIX = "lock:assistant:action:";

    private static final long ACTION_TTL_SECONDS = 900L;
    private static final long CONFIRM_TTL_SECONDS = 300L;
    private static final long STALE_PROCESSING_SECONDS = 60L;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_FAILED_RETRYABLE = "FAILED_RETRYABLE";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_EXPIRED = "EXPIRED";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private IActivityService activityService;

    @Resource
    private IRegistrationPassService registrationPassService;

    @Resource
    private ILimitedRegistrationQuotaService quotaService;

    @Resource
    private IActivityRegistrationService registrationService;

    @Resource
    private RedissonClient redissonClient;

    /** 校验报名条件并生成待确认操作。 */
    public Result prepareRegistration(Long activityId, Long registrationPassId) {
        if (registrationPassId == null) {
            return Result.fail("缺少报名凭证");
        }
        RegistrationPass pass = registrationPassService.getById(registrationPassId);
        if (pass == null || !Integer.valueOf(1).equals(pass.getStatus())) {
            return Result.fail("报名凭证不存在或已下架");
        }
        if (activityId != null && !activityId.equals(pass.getActivityId())) {
            return Result.fail("活动与报名凭证不匹配");
        }
        Activity activity = activityService.getById(pass.getActivityId());
        if (activity == null || !"PUBLISHED".equals(activity.getActivityStatus())) {
            return Result.fail("活动不存在或尚未发布");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getRegistrationDeadline() != null
                && activity.getRegistrationDeadline().isBefore(now)) {
            return Result.fail("该活动报名已经截止");
        }
        if (activity.getCapacity() != null
                && activity.getSold() != null
                && activity.getSold() >= activity.getCapacity()) {
            return Result.fail("该活动名额已满");
        }
        if (Integer.valueOf(1).equals(pass.getType())) {
            LimitedRegistrationQuota quota = quotaService.getById(pass.getId());
            if (quota == null || quota.getStock() == null || quota.getStock() <= 0) {
                return Result.fail("限量名额已抢完");
            }
            if (quota.getBeginTime() != null && quota.getBeginTime().isAfter(now)) {
                return Result.fail("报名尚未开始");
            }
            if (quota.getEndTime() != null && quota.getEndTime().isBefore(now)) {
                return Result.fail("报名已经结束");
            }
        }

        Long userId = UserHolder.getUser().getId();
        ActivityRegistration existing = registrationService.query()
                .eq("user_id", userId)
                .eq("registration_pass_id", registrationPassId)
                .ne("status", 4)
                .one();
        if (existing != null) {
            return Result.fail("你已经报名该活动，无需重复操作");
        }

        AssistantPendingActionDTO action = new AssistantPendingActionDTO();
        action.setActionType("register");
        action.setActivityId(activity.getId());
        action.setRegistrationPassId(pass.getId());
        action.setActivityName(activity.getName());
        action.setPassTitle(pass.getTitle());
        action.setSummary("确认报名「" + activity.getName() + "」的「" + pass.getTitle() + "」");
        return store(action);
    }

    /** 校验报名归属并生成取消确认操作。 */
    public Result prepareCancellation(Long registrationId) {
        if (registrationId == null) {
            return Result.fail("缺少报名记录");
        }
        Long userId = UserHolder.getUser().getId();
        ActivityRegistration registration = registrationService.getById(registrationId);
        if (registration == null || !userId.equals(registration.getUserId())) {
            return Result.fail("报名记录不存在");
        }
        if (!Integer.valueOf(1).equals(registration.getStatus())) {
            return Result.fail("当前报名状态不可取消");
        }
        RegistrationPass pass = registrationPassService.getById(registration.getRegistrationPassId());
        if (pass == null) {
            return Result.fail("报名凭证不存在");
        }
        Activity activity = activityService.getById(pass.getActivityId());
        if (activity != null && activity.getStartTime() != null
                && activity.getStartTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经开始，无法取消报名");
        }
        String activityName = activity == null ? pass.getTitle() : activity.getName();

        AssistantPendingActionDTO action = new AssistantPendingActionDTO();
        action.setActionType("cancel");
        action.setActivityId(pass.getActivityId());
        action.setRegistrationId(registration.getId());
        action.setRegistrationPassId(pass.getId());
        action.setActivityName(activityName);
        action.setPassTitle(pass.getTitle());
        action.setSummary("确认取消「" + activityName + "」的报名");
        return store(action);
    }

    /** 幂等确认动作；同一令牌重复请求返回同一状态，不重复执行。 */
    public Result confirm(String token) {
        UserDTO user = requireUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (StrUtil.isBlank(token) || token.length() > 128) {
            return Result.fail("确认令牌无效");
        }
        String key = actionKey(user.getId(), token);
        RLock lock = redissonClient.getLock(ACTION_LOCK_PREFIX + user.getId() + ":" + token);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!locked) {
                return currentStateResult(key, token, "操作正在处理中");
            }

            StoredAction stored = readState(key);
            if (stored == null) {
                return Result.ok(AssistantActionResultDTO.terminal(
                        token, null, STATUS_EXPIRED, "确认已过期，请重新发起操作", null));
            }
            if (isTerminal(stored.status)) {
                return Result.ok(toResult(stored));
            }
            long now = System.currentTimeMillis();
            if (STATUS_PENDING.equals(stored.status)
                    && stored.action.getExpiresAt() != null
                    && stored.action.getExpiresAt() < now) {
                stored.status = STATUS_EXPIRED;
                stored.updatedAt = now;
                saveState(key, stored);
                return Result.ok(terminalResult(stored, STATUS_EXPIRED, "确认已过期，请重新发起操作", null));
            }
            if (STATUS_PROCESSING.equals(stored.status)
                    && now - stored.updatedAt < STALE_PROCESSING_SECONDS * 1000L) {
                return Result.ok(toResult(stored));
            }

            String invalidReason = validateActionStillAvailable(stored.action);
            if (invalidReason != null) {
                stored.status = STATUS_FAILED;
                stored.updatedAt = System.currentTimeMillis();
                stored.result = AssistantActionResultDTO.terminal(
                        stored.action.getActionId(), stored.action.getActionType(),
                        STATUS_FAILED, invalidReason, stored.action.getRegistrationId());
                saveState(key, stored);
                return Result.ok(stored.result);
            }

            stored.status = STATUS_PROCESSING;
            stored.updatedAt = now;
            saveState(key, stored);

            Result executed = execute(stored.action);
            if (!Boolean.TRUE.equals(executed.getSuccess())) {
                String error = StrUtil.blankToDefault(executed.getErrorMsg(), "操作执行失败");
                if (isIdempotentSuccess(error, stored.action.getActionType())) {
                    return Result.ok(markSuccess(key, stored, "操作已生效"));
                }
                stored.status = STATUS_FAILED_RETRYABLE;
                stored.updatedAt = System.currentTimeMillis();
                stored.result = retryableResult(stored, error);
                saveState(key, stored);
                return Result.ok(stored.result);
            }
            return Result.ok(applyExecutionResult(key, stored, executed.getData()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("确认请求被中断，请重试");
        } catch (Exception e) {
            log.error("assistant action confirmation failed", e);
            return Result.fail("确认服务暂时不可用，请稍后重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 查询动作状态，并在限量报名处理中时刷新后台结果。 */
    public Result status(String token) {
        UserDTO user = requireUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (StrUtil.isBlank(token) || token.length() > 128) {
            return Result.fail("动作标识无效");
        }
        String key = actionKey(user.getId(), token);
        StoredAction stored = readState(key);
        if (stored == null) {
            return Result.ok(AssistantActionResultDTO.terminal(
                    token, null, STATUS_EXPIRED, "动作不存在或已经过期", null));
        }
        refreshProcessingStatus(key, stored);
        return Result.ok(toResult(stored));
    }

    private String validateActionStillAvailable(AssistantPendingActionDTO action) {
        if ("register".equals(action.getActionType())) {
            RegistrationPass pass = registrationPassService.getById(action.getRegistrationPassId());
            if (pass == null || !Integer.valueOf(1).equals(pass.getStatus())) {
                return "报名凭证已失效";
            }
            Activity activity = activityService.getById(pass.getActivityId());
            if (activity == null || !"PUBLISHED".equals(activity.getActivityStatus())) {
                return "活动已下架";
            }
            LocalDateTime now = LocalDateTime.now();
            if (activity.getRegistrationDeadline() != null
                    && activity.getRegistrationDeadline().isBefore(now)) {
                return "活动报名已经截止";
            }
            if (activity.getCapacity() != null && activity.getSold() != null
                    && activity.getSold() >= activity.getCapacity()) {
                return "活动名额已满";
            }
            if (Integer.valueOf(1).equals(pass.getType())) {
                LimitedRegistrationQuota quota = quotaService.getById(pass.getId());
                if (quota == null || quota.getStock() == null || quota.getStock() <= 0) {
                    return "限量名额已抢完";
                }
                if (quota.getBeginTime() != null && quota.getBeginTime().isAfter(now)) {
                    return "报名尚未开始";
                }
                if (quota.getEndTime() != null && quota.getEndTime().isBefore(now)) {
                    return "报名已经结束";
                }
            }
        } else if ("cancel".equals(action.getActionType())) {
            ActivityRegistration registration = registrationService.getById(action.getRegistrationId());
            if (registration == null) {
                return "报名记录不存在";
            }
            if (Integer.valueOf(4).equals(registration.getStatus())) {
                return null;
            }
            if (!Integer.valueOf(1).equals(registration.getStatus())) {
                return "当前报名状态不可取消";
            }
            RegistrationPass pass = registrationPassService.getById(registration.getRegistrationPassId());
            if (pass == null) {
                return "报名凭证不存在";
            }
            Activity activity = activityService.getById(pass.getActivityId());
            if (activity != null && activity.getStartTime() != null
                    && activity.getStartTime().isBefore(LocalDateTime.now())) {
                return "活动已经开始，无法取消报名";
            }
        }
        return null;
    }

    private Result execute(AssistantPendingActionDTO action) {
        if ("register".equals(action.getActionType())) {
            return registrationService.register(action.getRegistrationPassId());
        }
        if ("cancel".equals(action.getActionType())) {
            return registrationService.cancel(action.getRegistrationId());
        }
        return Result.fail("不支持的助手操作");
    }

    private AssistantActionResultDTO applyExecutionResult(
            String key, StoredAction stored, Object data) {
        if (data instanceof RegistrationStatusDTO) {
            return applyRegistrationStatus(key, stored, (RegistrationStatusDTO) data);
        }
        Long registrationId = null;
        if (data != null) {
            try {
                registrationId = Long.valueOf(data.toString());
            } catch (NumberFormatException ignored) {
                registrationId = stored.action.getRegistrationId();
            }
        }
        String message = "cancel".equals(stored.action.getActionType())
                ? "报名已取消" : "报名成功";
        return markSuccess(key, stored, message, registrationId);
    }

    private AssistantActionResultDTO applyRegistrationStatus(
            String key, StoredAction stored, RegistrationStatusDTO status) {
        String state = status.getStatus();
        Long registrationId = status.getRegistrationId();
        if ("SUCCESS".equals(state)) {
            return markSuccess(key, stored, "报名成功", registrationId);
        }
        if ("FAILED".equals(state) || "COMPENSATED".equals(state)) {
            stored.status = STATUS_FAILED;
            stored.updatedAt = System.currentTimeMillis();
            stored.result = AssistantActionResultDTO.terminal(
                    stored.action.getActionId(), stored.action.getActionType(), STATUS_FAILED,
                    StrUtil.blankToDefault(status.getFailureReason(), "报名失败，名额已回补"),
                    registrationId);
            saveState(key, stored);
            return stored.result;
        }
        stored.status = STATUS_PROCESSING;
        stored.updatedAt = System.currentTimeMillis();
        stored.result = AssistantActionResultDTO.processing(
                stored.action.getActionId(), stored.action.getActionType(),
                "报名已受理，正在确认名额", registrationId);
        saveState(key, stored);
        return stored.result;
    }

    private void refreshProcessingStatus(String key, StoredAction stored) {
        if (!STATUS_PROCESSING.equals(stored.status)
                || stored.action == null
                || stored.result == null
                || stored.result.getRegistrationId() == null) {
            return;
        }
        Result refreshed = registrationService.queryRegistrationStatus(
                stored.result.getRegistrationId());
        if (Boolean.TRUE.equals(refreshed.getSuccess())
                && refreshed.getData() instanceof RegistrationStatusDTO) {
            applyRegistrationStatus(key, stored, (RegistrationStatusDTO) refreshed.getData());
        }
    }

    private AssistantActionResultDTO markSuccess(String key, StoredAction stored, String message) {
        return markSuccess(key, stored, message, stored.action.getRegistrationId());
    }

    private AssistantActionResultDTO markSuccess(
            String key, StoredAction stored, String message, Long registrationId) {
        String status = "cancel".equals(stored.action.getActionType())
                ? STATUS_CANCELLED : STATUS_SUCCESS;
        stored.status = status;
        stored.updatedAt = System.currentTimeMillis();
        stored.result = AssistantActionResultDTO.terminal(
                stored.action.getActionId(), stored.action.getActionType(), status, message, registrationId);
        saveState(key, stored);
        return stored.result;
    }

    private Result currentStateResult(String key, String token, String fallbackMessage) {
        StoredAction stored = readState(key);
        if (stored == null) {
            return Result.fail("确认服务繁忙，请稍后重试");
        }
        return Result.ok(toResult(stored));
    }

    private Result store(AssistantPendingActionDTO action) {
        String actionId = newToken();
        action.setActionId(actionId);
        action.setConfirmationToken(actionId);
        action.setExpiresInSeconds(CONFIRM_TTL_SECONDS);
        action.setExpiresAt(System.currentTimeMillis() + CONFIRM_TTL_SECONDS * 1000L);
        StoredAction stored = new StoredAction();
        stored.userId = UserHolder.getUser().getId();
        stored.status = STATUS_PENDING;
        stored.createdAt = System.currentTimeMillis();
        stored.updatedAt = stored.createdAt;
        stored.action = action;
        String key = actionKey(stored.userId, actionId);
        try {
            saveState(key, stored);
            return Result.ok(action);
        } catch (Exception e) {
            log.error("unable to store assistant action", e);
            return Result.fail("暂时无法创建确认操作，请稍后重试");
        }
    }

    private StoredAction readState(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            return StrUtil.isBlank(json) ? null : objectMapper.readValue(json, StoredAction.class);
        } catch (Exception e) {
            log.warn("unable to read assistant action state", e);
            return null;
        }
    }

    private void saveState(String key, StoredAction stored) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(stored),
                    ACTION_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("unable to persist assistant action state", e);
        }
    }

    private AssistantActionResultDTO toResult(StoredAction stored) {
        if (stored.result != null) {
            stored.result.setActionId(stored.action.getActionId());
            return stored.result;
        }
        if (STATUS_EXPIRED.equals(stored.status)) {
            return terminalResult(stored, STATUS_EXPIRED, "确认已过期，请重新发起操作", null);
        }
        if (STATUS_FAILED_RETRYABLE.equals(stored.status)) {
            return retryableResult(stored, "操作未完成，请重试");
        }
        return AssistantActionResultDTO.processing(
                stored.action.getActionId(), stored.action.getActionType(),
                "操作正在处理中", stored.action.getRegistrationId());
    }

    private AssistantActionResultDTO terminalResult(
            StoredAction stored, String status, String message, Long registrationId) {
        return AssistantActionResultDTO.terminal(
                stored.action.getActionId(), stored.action.getActionType(), status, message, registrationId);
    }

    private AssistantActionResultDTO retryableResult(StoredAction stored, String message) {
        AssistantActionResultDTO result = AssistantActionResultDTO.terminal(
                stored.action.getActionId(), stored.action.getActionType(),
                STATUS_FAILED_RETRYABLE, message, stored.action.getRegistrationId());
        result.setRetryable(true);
        return result;
    }

    private boolean isTerminal(String status) {
        return STATUS_SUCCESS.equals(status)
                || STATUS_CANCELLED.equals(status)
                || STATUS_FAILED.equals(status)
                || STATUS_EXPIRED.equals(status);
    }

    private boolean isIdempotentSuccess(String message, String actionType) {
        if (message == null) {
            return false;
        }
        if ("register".equals(actionType)) {
            return message.contains("已经报名") || message.contains("不能重复报名");
        }
        return message.contains("已经取消");
    }

    private UserDTO requireUser() {
        return UserHolder.getUser();
    }

    private String actionKey(Long userId, String token) {
        return ACTION_KEY_PREFIX + userId + ":" + token;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Redis 中保存的动作状态，不返回给前端。 */
    public static class StoredAction {
        public Long userId;
        public String status;
        public long createdAt;
        public long updatedAt;
        public AssistantPendingActionDTO action;
        public AssistantActionResultDTO result;
    }
}
