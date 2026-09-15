package com.campuspilot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campuspilot.dto.RegistrationStatusDTO;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.RegistrationViewDTO;
import com.campuspilot.entity.Activity;
import com.campuspilot.entity.ActivityRegistration;
import com.campuspilot.entity.LimitedRegistrationQuota;
import com.campuspilot.entity.RegistrationPass;
import com.campuspilot.entity.RegistrationRequest;
import com.campuspilot.mapper.ActivityRegistrationMapper;
import com.campuspilot.registration.RegistrationMessage;
import com.campuspilot.registration.RegistrationMetrics;
import com.campuspilot.registration.RegistrationMqProperties;
import com.campuspilot.registration.RegistrationRequestRepository;
import com.campuspilot.registration.RegistrationReservationService;
import com.campuspilot.registration.RegistrationStatus;
import com.campuspilot.service.IActivityRegistrationService;
import com.campuspilot.service.ILimitedRegistrationQuotaService;
import com.campuspilot.service.IRegistrationPassService;
import com.campuspilot.service.IActivityService;
import com.campuspilot.utils.RedisIdWorker;
import com.campuspilot.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.aop.framework.AopContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 实现活动报名的业务规则与持久化协调。 */
@Slf4j
@Service
public class ActivityRegistrationServiceImpl
        extends ServiceImpl<ActivityRegistrationMapper, ActivityRegistration>
        implements IActivityRegistrationService {

    @Resource
    private ILimitedRegistrationQuotaService quotaService;
    @Resource
    private IRegistrationPassService passService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IActivityService activityService;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private RegistrationMqProperties mqProperties;
    @Resource
    private RegistrationRequestRepository requestRepository;
    @Resource
    private RegistrationReservationService reservationService;
    @Resource
    private RegistrationMetrics registrationMetrics;

    /** 根据凭证类型选择普通或限量报名流程。 */
    @Override
    public Result register(Long registrationPassId) {
        RegistrationPass pass = passService.getById(registrationPassId);

        // 1. 查这张"票种"存不存在、有没有下架
        if (pass == null || !Integer.valueOf(1).equals(pass.getStatus())) {
            return Result.fail("报名凭证不存在或已下架");
        }

        // 2. 如果是限量票（type=1），转到复杂的限量流程
        if (Integer.valueOf(1).equals(pass.getType())) {
            return registerForLimitedActivity(registrationPassId);
        }
        Long userId = UserHolder.getUser().getId();

        // 普通报名也按"用户+凭证"加锁，拦截连续点击和跨节点并发。
        RLock lock = redissonClient.getLock("lock:registration:" + userId + ":" + registrationPassId);

        try {
            if (!lock.tryLock(1, 10, TimeUnit.SECONDS)) {
                return Result.fail("报名处理中，请勿重复提交");
            }
            // 4. 造一条报名记录（生成一个全局唯一的 ID）
            ActivityRegistration order = buildRegistration(userId, registrationPassId);
            // 5. 真正写进数据库
            currentProxy().createActivityRegistration(order);
            return Result.ok(order.getId());
        } catch (DuplicateKeyException e) {
            return Result.fail("不能重复报名");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("报名请求被中断，请重试");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();// 用完释放锁
        }
    }

    /*
    1.首先校验报名凭证是否合法
    2.检查用户之前提交过报名请求没
    2.1用户没提交报名请求-》新建流水pending发事务消息给RocketMQ
    2.2用户已经提交过报名请求-》请求的状态是不是失败或者已经取消的状态
    2.2.1请求的状态是失败或者已经取消的状态，先通过resetForRetry方法重置数据库中的信息，重置成功后再发事务消息给RocketMQ
    2.2.2除了 FAILED / COMPENSATED 之外的所有状态都直接返回当前状态
     */
    @Override
    public Result registerForLimitedActivity(Long registrationPassId) {

        RegistrationPass pass = passService.getById(registrationPassId);
        //校验报名凭证是否合法
        if (pass == null || !Integer.valueOf(1).equals(pass.getStatus())//status为1表示已经上架，status=0表示已经下架
                || !Integer.valueOf(1).equals(pass.getType())) {//type = 0 → 普通报名 type = 1 → 限量报名
            return Result.fail("限量报名凭证不存在或已下架");
        }
        //查询限量报名凭证是否有效
        LimitedRegistrationQuota quota = quotaService.getById(registrationPassId);
        if (quota == null) return Result.fail("限量报名不存在");
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        if (quota.getBeginTime() != null && quota.getBeginTime().isAfter(now)) {
            return Result.fail("报名尚未开始");
        }
        if (quota.getEndTime() != null && quota.getEndTime().isBefore(now)) {
            return Result.fail("报名已经结束");
        }
        if (!mqProperties.isEnabled()) {//如果MQ功能关闭这里会直接拒绝报名
            return Result.fail("报名服务暂不可用");
        }

        Long userId = UserHolder.getUser().getId();
        RegistrationRequest existing = requestRepository.findByUserAndPass(userId, registrationPassId);
//        说明用户之前已经提交过报名请求。
        if (existing != null) {
            //判断之前报名是否失败
//            FAILED 可能是消费者执行失败。COMPENSATED 表示一般意味着事务或者异常情况下进行了补偿。
            if (RegistrationStatus.FAILED.name().equals(existing.getStatus()) ||
                    RegistrationStatus.COMPENSATED.name().equals(existing.getStatus())) {
                /* 失败/已取消 → 重置为 PENDING 重新来 */
                String eventId = UUID.randomUUID().toString().replace("-", "");
//                resetForRetry这个方法是个原子条件更新；if(重置成功的话)
                if (requestRepository.resetForRetry(existing.getRegistrationId(), eventId) == 1) {
                    existing.setEventId(eventId);
                    existing.setStatus(RegistrationStatus.PENDING.name());
                    existing.setFailureCode(null);
                    existing.setFailureReason(null);
//                    封装报名的信息
                    RegistrationMessage retryPayload = new RegistrationMessage(
                            eventId, existing.getRegistrationId(), userId, registrationPassId,
                            System.currentTimeMillis(), 1);
//                    重新发送事务信息
                    return sendTransaction(existing, retryPayload);
                }
                //重置失败的话，就重新查库返回当前的状态
                existing = requestRepository.findById(existing.getRegistrationId());
            }

            return Result.ok(toStatus(existing));
        }

        long registrationId = redisIdWorker.nextId("registration");
        RegistrationMessage payload = new RegistrationMessage(
                UUID.randomUUID().toString().replace("-", ""), registrationId, userId,
                registrationPassId, System.currentTimeMillis(), 1);
        RegistrationRequest request = new RegistrationRequest();
        request.setRegistrationId(registrationId);
        request.setUserId(userId);
        request.setRegistrationPassId(registrationPassId);
        request.setEventId(payload.getEventId());
        request.setStatus(RegistrationStatus.PENDING.name());
        request.setRetryCount(0);
        request.setVersion(0);
        request.setCreateTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        try {
            requestRepository.insert(request);
        } catch (DuplicateKeyException e) {
            RegistrationRequest duplicate = requestRepository.findByUserAndPass(userId, registrationPassId);
            return duplicate == null
                    ? Result.fail("报名请求冲突，请稍后重试")
                    : Result.ok(toStatus(duplicate));
        }
        return sendTransaction(request, payload);
    }

    private Result sendTransaction(RegistrationRequest request, RegistrationMessage payload) {
        Long registrationId = request.getRegistrationId();
        Message<RegistrationMessage> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, registrationId.toString())
                .setHeader("registrationId", registrationId.toString())
                .build();
        try {
//            保证redis占座成功和发送消息到RocketMQ具有事务性，两个动作是一气呵成的
            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(
                    mqProperties.createDestination(), message, payload);
            requestRepository.updateMessageId(registrationId, sendResult.getMsgId());
            RegistrationRequest latest = requestRepository.findById(registrationId);
            if (sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
                return Result.fail(latest == null || latest.getFailureReason() == null
                        ? "不满足活动报名条件" : latest.getFailureReason());
            }
            registrationMetrics.accepted();
            log.info("registration accepted registrationId={} userId={} passId={} messageId={} txState={}",
                    registrationId, request.getUserId(), request.getRegistrationPassId(),
                    sendResult.getMsgId(), sendResult.getLocalTransactionState());
            return Result.ok(toStatus(latest == null ? request : latest));
        } catch (Exception e) {
            registrationMetrics.sendFailed();
            String reservationState = null;
            try {
                reservationState = reservationService.transactionState(
                        request.getRegistrationPassId(), registrationId);
            } catch (Exception redisException) {
                requestRepository.updateStatus(registrationId, RegistrationStatus.PENDING,
                        "MESSAGE_SEND_UNCERTAIN", "消息与资格预占状态未知，系统正在恢复");
                log.error("cannot determine registration reservation after send failure registrationId={}",
                        registrationId, redisException);
                return Result.ok(new RegistrationStatusDTO(registrationId,
                        RegistrationStatus.PENDING.name(), "MESSAGE_SEND_UNCERTAIN", "系统正在恢复报名请求"));
            }
            if (RegistrationStatus.RESERVED.name().equals(reservationState) ||
                    RegistrationStatus.PROCESSING.name().equals(reservationState)) {
                requestRepository.updateStatus(registrationId, RegistrationStatus.RESERVED,
                        "MESSAGE_SEND_UNCERTAIN", "消息发送结果未知，系统正在恢复");
                return Result.ok(new RegistrationStatusDTO(registrationId,
                        RegistrationStatus.RESERVED.name(), "MESSAGE_SEND_UNCERTAIN", "系统正在恢复报名请求"));
            }
            requestRepository.updateStatus(registrationId, RegistrationStatus.FAILED,
                    "MESSAGE_SEND_FAILED", "报名消息发送失败");
            log.error("registration message send failed registrationId={} userId={} passId={}",
                    registrationId, request.getUserId(), request.getRegistrationPassId(), e);
            return Result.fail("报名请求发送失败，请稍后重试");
        }
    }

    /** 只允许报名流水所属用户查看异步处理状态。 */
    @Override
    public Result queryRegistrationStatus(Long registrationId) {
        RegistrationRequest request = requestRepository.findById(registrationId);
        if (request == null) return Result.fail("报名流水不存在");
        if (!UserHolder.getUser().getId().equals(request.getUserId())) {
            return Result.fail("无权查看该报名流水");
        }
        return Result.ok(toStatus(request));
    }

    private RegistrationStatusDTO toStatus(RegistrationRequest request) {
        return new RegistrationStatusDTO(request.getRegistrationId(), request.getStatus(),
                request.getFailureCode(), request.getFailureReason());
    }

    /** 在数据库事务中创建报名记录并同步数量。 */
    @Override
    @Transactional
    /*
    报名的逻辑：
    报名过但是取消了：还是有记录在数据库中，需要修改对应的状态
    报名过但是没取消：直接返回重复报名
    没报名过：直接新增报名
     */
    public void createActivityRegistration(ActivityRegistration registration) {
        // 1. 查这个人是不是已经报过这个活动了
        ActivityRegistration existing = query().eq("user_id", registration.getUserId())
                .eq("registration_pass_id", registration.getRegistrationPassId()).one();
        // 2. 报过了且没取消 → 直接报错"不能重复报名"
        if (existing != null && !Integer.valueOf(4).equals(existing.getStatus())) {
            throw new DuplicateKeyException("duplicate registration");
        }
        // 3. 查票种
        RegistrationPass pass = passService.getById(registration.getRegistrationPassId());
        if (pass == null) throw new IllegalStateException("报名凭证不存在");
        // 4. 如果是限量票：数据库名额也减 1（作为 Redis 预占之后的最后防线）
        if (Integer.valueOf(1).equals(pass.getType())) {
            // 数据库仍做 stock > 0 的条件更新，作为 Redis 预扣之后的最终一致性防线。
            boolean updated = quotaService.update().setSql("stock = stock - 1")
                    .eq("registration_pass_id", registration.getRegistrationPassId())
                    .gt("stock", 0).update();
            if (!updated) throw new IllegalStateException("数据库名额不足");
        }
        // 5. 之前取消过 → 把旧记录"复活"；否则插入一条新记录
        if (existing != null) { // ① 走到这里，existing 一定是"已取消"的旧记录
            existing.setStatus(1);// ② 把状态改回 1（已报名）
            existing.setPayType(1); // ③ 支付方式重置为默认（1）
            if (!updateById(existing)) throw new IllegalStateException("报名记录恢复失败");
            registration.setId(existing.getId());// ④ 按主键更新这条旧记录
        } else if (!save(registration)) { // ⑥ 之前没报过 → 插入新记录
            throw new IllegalStateException("报名记录保存失败");
        }
        incrementActivitySold(pass.getActivityId());
    }

    /** 查询当前用户的报名记录并组装展示数据。 */
    @Override
    public Result queryMyRegistrations() {
        Long userId = UserHolder.getUser().getId();
        List<ActivityRegistration> list = query().eq("user_id", userId)
                .orderByDesc("create_time").list();
        List<RegistrationViewDTO> views = new java.util.ArrayList<>();
        for (ActivityRegistration registration : list) {
            RegistrationViewDTO view = new RegistrationViewDTO();
            view.setId(registration.getId());
            view.setRegistrationPassId(registration.getRegistrationPassId());
            view.setStatus(registration.getStatus());
            view.setCreateTime(registration.getCreateTime());
            RegistrationPass pass = passService.getById(registration.getRegistrationPassId());
            if (pass != null) {
                view.setTitle(pass.getTitle());
                view.setActivityId(pass.getActivityId());
                Activity activity = activityService.getById(pass.getActivityId());
                if (activity != null) {
                    view.setActivityName(activity.getName());
                    view.setAddress(activity.getAddress());
                    view.setOpenHours(activity.getOpenHours());
                }
            }
            views.add(view);
        }
        return Result.ok(views);
    }

    /** 校验报名归属后，通过事务代理执行取消。 */
    @Override
    public Result cancel(Long registrationId) {
        Long userId = UserHolder.getUser().getId();
        ActivityRegistration registration = getById(registrationId);
        if (registration == null || !userId.equals(registration.getUserId())) {
            return Result.fail("报名记录不存在");
        }
        if (Integer.valueOf(4).equals(registration.getStatus())) return Result.fail("报名已经取消");
        currentProxy().cancelRegistration(registration);
        return Result.ok();
    }

    /**
     * 在事务中取消报名，并必要时回补限量库存。
     * Redis 回补延后到事务提交后执行：即使后续 DB 步骤失败导致事务回滚，
     * Redis 也不会先于 DB 变更，避免两套库存源漂移。
     */
    @Override
    @Transactional
    public void cancelRegistration(ActivityRegistration registration) {
        RegistrationPass pass = passService.getById(registration.getRegistrationPassId());
        if (pass == null) throw new IllegalStateException("报名凭证不存在");
        boolean updated = update().set("status", 4).eq("id", registration.getId())
                .ne("status", 4).update();
        if (!updated) throw new IllegalStateException("取消报名失败");
        try {
            activityService.decrementSold(pass.getActivityId());
        } catch (Exception e) {
            log.warn("decrement activity sold failed activityId={}", pass.getActivityId(), e);
        }
        if (Integer.valueOf(1).equals(pass.getType())) {
            quotaService.update().setSql("stock = stock + 1")
                    .eq("registration_pass_id", registration.getRegistrationPassId()).update();
            requestRepository.markCancelled(registration.getId());
            registerAfterCommit(() -> compensateInventory(registration));
        }
    }

    /** 事务提交后回补 Redis 预占；失败则有限重试，剩余交给对账任务兜底。 */
    private void compensateInventory(ActivityRegistration registration) {
        int attempts = 0;
        while (true) {
            try {
                if (reservationService.rollback(registration.getRegistrationPassId(),
                        registration.getUserId(), registration.getId())) {
                    return;
                }
            } catch (Exception e) {
                log.error("Redis 名额回补失败 registrationId={}", registration.getId(), e);
            }
            attempts++;
            if (attempts >= 3) {
                log.error("Redis 名额回补多次失败，等待对账任务兜底 registrationId={}", registration.getId());
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 同步活动"已报名"人数；计数失败不阻塞报名主流程。 */
    private void incrementActivitySold(Long activityId) {
        try {
            activityService.incrementSold(activityId);
        } catch (Exception e) {
            log.warn("increment activity sold failed activityId={}", activityId, e);
        }
    }

    /** 使用全局 ID 构造待持久化的报名记录。 */
    private ActivityRegistration buildRegistration(Long userId, Long passId) {
        ActivityRegistration registration = new ActivityRegistration();
        registration.setId(redisIdWorker.nextId("registration"));
        registration.setUserId(userId);
        registration.setRegistrationPassId(passId);
        registration.setStatus(1);
        registration.setPayType(1);
        return registration;
    }

    /** 取得 Spring 事务代理，确保类内调用也能触发事务。 */
    private IActivityRegistrationService currentProxy() {
        return (IActivityRegistrationService) AopContext.currentProxy();
    }

    /** 注册事务提交后的回调。 */
    private void registerAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
