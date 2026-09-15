package com.campuspilot.registration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuspilot.entity.RegistrationRequest;
import com.campuspilot.mapper.RegistrationRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RegistrationRequestRepository {
    private final RegistrationRequestMapper mapper;

    public int insert(RegistrationRequest request) {
        return mapper.insert(request);
    }

    public RegistrationRequest findById(Long registrationId) {
        return mapper.selectById(registrationId);
    }

    public RegistrationRequest findByUserAndPass(Long userId, Long registrationPassId) {
        return mapper.selectOne(new LambdaQueryWrapper<RegistrationRequest>()
                .eq(RegistrationRequest::getUserId, userId)
                .eq(RegistrationRequest::getRegistrationPassId, registrationPassId)
                .last("LIMIT 1"));
    }

    public int updateStatus(Long registrationId, RegistrationStatus status,
                            String failureCode, String failureReason) {
        LambdaUpdateWrapper<RegistrationRequest> update = new LambdaUpdateWrapper<RegistrationRequest>()
                .eq(RegistrationRequest::getRegistrationId, registrationId)
                .set(RegistrationRequest::getStatus, status.name())
                .set(RegistrationRequest::getFailureCode, failureCode)
                .set(RegistrationRequest::getFailureReason, truncate(failureReason, 255))
                .set(RegistrationRequest::getUpdateTime, LocalDateTime.now());
        if (status != RegistrationStatus.SUCCESS) {
            update.ne(RegistrationRequest::getStatus, RegistrationStatus.SUCCESS.name());
        }
        if (status == RegistrationStatus.PENDING || status == RegistrationStatus.RESERVED ||
                status == RegistrationStatus.PROCESSING) {
            update.ne(RegistrationRequest::getStatus, RegistrationStatus.FAILED.name())
                    .ne(RegistrationRequest::getStatus, RegistrationStatus.COMPENSATED.name());
        }
        if (status == RegistrationStatus.FAILED) {
            update.ne(RegistrationRequest::getStatus, RegistrationStatus.COMPENSATED.name());
        }
        return mapper.update(null, update);
    }

    public void updateMessageId(Long registrationId, String messageId) {
        if (messageId == null) {
            return;
        }
        mapper.update(null, new LambdaUpdateWrapper<RegistrationRequest>()
                .eq(RegistrationRequest::getRegistrationId, registrationId)
                .set(RegistrationRequest::getMessageId, messageId)
                .set(RegistrationRequest::getUpdateTime, LocalDateTime.now()));
    }

    public void incrementRetry(Long registrationId) {
        mapper.update(null, new LambdaUpdateWrapper<RegistrationRequest>()
                .eq(RegistrationRequest::getRegistrationId, registrationId)
                .setSql("retry_count = retry_count + 1")
                .set(RegistrationRequest::getUpdateTime, LocalDateTime.now()));
    }

    public int resetForRetry(Long registrationId, String eventId) {
        return mapper.update(null, new LambdaUpdateWrapper<RegistrationRequest>()
                .eq(RegistrationRequest::getRegistrationId, registrationId)
                .in(RegistrationRequest::getStatus, Arrays.asList(
                        RegistrationStatus.FAILED.name(), RegistrationStatus.COMPENSATED.name()))
                .set(RegistrationRequest::getEventId, eventId)
                .set(RegistrationRequest::getMessageId, null)
                .set(RegistrationRequest::getStatus, RegistrationStatus.PENDING.name())
                .set(RegistrationRequest::getRetryCount, 0)
                .set(RegistrationRequest::getFailureCode, null)
                .set(RegistrationRequest::getFailureReason, null)
                .set(RegistrationRequest::getUpdateTime, LocalDateTime.now()));
    }

    /** 用户取消已成功报名后关闭流水，允许后续使用同一流水重新报名。 */
    public int markCancelled(Long registrationId) {
        return mapper.update(null, new LambdaUpdateWrapper<RegistrationRequest>()
                .eq(RegistrationRequest::getRegistrationId, registrationId)
                .eq(RegistrationRequest::getStatus, RegistrationStatus.SUCCESS.name())
                .set(RegistrationRequest::getStatus, RegistrationStatus.COMPENSATED.name())
                .set(RegistrationRequest::getFailureCode, "USER_CANCELLED")
                .set(RegistrationRequest::getFailureReason, "用户已取消报名")
                .set(RegistrationRequest::getUpdateTime, LocalDateTime.now()));
    }

    public List<RegistrationRequest> findStale(LocalDateTime before, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<RegistrationRequest>()
                .in(RegistrationRequest::getStatus, Arrays.asList(
                        RegistrationStatus.PENDING.name(),
                        RegistrationStatus.RESERVED.name(),
                        RegistrationStatus.PROCESSING.name()))
                .lt(RegistrationRequest::getUpdateTime, before)
                .orderByAsc(RegistrationRequest::getUpdateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    /** 查找"已取消但 Redis 回补可能未完成"的流水，供对账任务幂等重放回补。 */
    public List<RegistrationRequest> findCancelledUncompensated(LocalDateTime before, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<RegistrationRequest>()
                .eq(RegistrationRequest::getStatus, RegistrationStatus.COMPENSATED.name())
                .eq(RegistrationRequest::getFailureCode, "USER_CANCELLED")
                .lt(RegistrationRequest::getUpdateTime, before)
                .orderByAsc(RegistrationRequest::getUpdateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
