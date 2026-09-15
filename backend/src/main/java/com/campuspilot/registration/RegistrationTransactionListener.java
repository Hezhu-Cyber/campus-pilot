package com.campuspilot.registration;

import com.campuspilot.entity.RegistrationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;

@Slf4j
@RequiredArgsConstructor
@RocketMQTransactionListener
@ConditionalOnProperty(prefix = "app.registration.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationTransactionListener implements RocketMQLocalTransactionListener {
    private final RegistrationReservationService reservationService;
    private final RegistrationRequestRepository requestRepository;
    private final RegistrationMessageCodec codec;
    private final RegistrationMetrics metrics;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object arg) {
        RegistrationMessage registrationMessage = arg instanceof RegistrationMessage
                ? (RegistrationMessage) arg : codec.decode(message);
        try {
            //调用Lua脚本占座
            RegistrationReserveResult result = reservationService.reserve(registrationMessage);
            if (result != RegistrationReserveResult.SUCCESS) {
                // 占座失败（没名额/重复/没开始...）→ 流水记为 FAILED，消息不投递
                requestRepository.updateStatus(registrationMessage.getRegistrationId(),
                        RegistrationStatus.FAILED, result.name(), result.getMessage());
                metrics.rejected();
                log.info("registration rejected registrationId={} userId={} passId={} reason={}",
                        registrationMessage.getRegistrationId(), registrationMessage.getUserId(),
                        registrationMessage.getRegistrationPassId(), result.name());
                return RocketMQLocalTransactionState.ROLLBACK;//撤销投递
            }
            // 占座成功 → 流水记为 RESERVED（已占座），消息可以投递
            requestRepository.updateStatus(registrationMessage.getRegistrationId(),
                    RegistrationStatus.RESERVED, null, null);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("registration local transaction uncertain registrationId={}",
                    registrationMessage.getRegistrationId(), e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }


    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        RegistrationMessage registrationMessage;
        try {
            // 把 MQ 消息还原成 RegistrationMessage
            registrationMessage = codec.decode(message);
        } catch (Exception e) {
//            解析失败 → 返回 UNKNOWN（"我还没法判断，你过会儿再问"）。
            log.error("cannot decode transaction check message", e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }

        try {
            // 看 Redis 里这个报名的状态
            String redisState = reservationService.transactionState(
                    registrationMessage.getRegistrationPassId(), registrationMessage.getRegistrationId());
//            Redis 状态是 RESERVED / PROCESSING / SUCCESS →
//            说明预占已经成功（甚至已经落库）→ 顺手把状态同步到数据库（updateStatus），
//            返回 COMMIT（消息该投递，消费者去落库）；
            if (RegistrationStatus.RESERVED.name().equals(redisState) ||
                    RegistrationStatus.PROCESSING.name().equals(redisState) ||
                    RegistrationStatus.SUCCESS.name().equals(redisState)) {
                requestRepository.updateStatus(registrationMessage.getRegistrationId(),
                        RegistrationStatus.valueOf(redisState), null, null);
                return RocketMQLocalTransactionState.COMMIT;
            }
            //Redis 状态是 COMPENSATED → 说明名额已经补偿还回去了 → 返回 ROLLBACK（别投递了，座位都没了）。
            if (RegistrationStatus.COMPENSATED.name().equals(redisState)) {
                return RocketMQLocalTransactionState.ROLLBACK;
            }
            //③ Redis 没状态（key 过期/被清）→ 退而查数据库
            RegistrationRequest request = requestRepository.findById(registrationMessage.getRegistrationId());
            if (request == null || RegistrationStatus.FAILED.name().equals(request.getStatus()) ||
                    RegistrationStatus.COMPENSATED.name().equals(request.getStatus())) {
                return RocketMQLocalTransactionState.ROLLBACK;//流水不存在，或状态是 FAILED / COMPENSATED → ROLLBACK；
            }
            if (RegistrationStatus.RESERVED.name().equals(request.getStatus()) ||
                    RegistrationStatus.PROCESSING.name().equals(request.getStatus()) ||
                    RegistrationStatus.SUCCESS.name().equals(request.getStatus())) {
                return RocketMQLocalTransactionState.COMMIT;//状态是 RESERVED / PROCESSING / SUCCESS → COMMIT；
            }
//            ④ 任何异常 → UNKNOWN（宁可拖着，也别乱投递）。
            return RocketMQLocalTransactionState.UNKNOWN;
        } catch (Exception e) {
            log.error("registration transaction check failed registrationId={}",
                    registrationMessage.getRegistrationId(), e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
