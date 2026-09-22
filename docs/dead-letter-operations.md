# 报名死信无人值守处理

## 处理原则

- `RETRY_EXHAUSTED` 等可恢复错误由定时任务自动重放，默认最多 3 次，并使用指数退避。
- 非法载荷、业务永久错误不会反复重放，直接标记为 `MANUAL_REQUIRED`。
- 自动重放仍失败且达到上限时，同样转人工，并通过 Webhook 发送告警。
- 多实例通过数据库条件更新抢占死信，避免同一条记录被重复重放。
- 实例在重放过程中崩溃时，超时未更新的 `AUTO_RETRYING` 记录会被重新放回队列。
- 自定义死信 Topic 不可用时，生产者会直接写入死信表作为本地兜底。

## 状态流转

```text
PENDING -> AUTO_RETRYING -> AUTO_REPLAYED
                         -> PENDING（等待下一次退避重试）
                         -> MANUAL_REQUIRED

PENDING -> MANUAL_REQUIRED（业务永久错误或非法载荷）

PENDING / MANUAL_REQUIRED -> REPLAYED（管理员人工重放成功）
```

## 查询待处理死信

已有数据库需要先执行可重复运行的迁移脚本：

```text
backend/src/main/resources/db/rocketmq_registration_migration.sql
```

迁移会补充 `retry_count`、`next_retry_time`、`last_retry_time` 和自动重试索引。

```sql
SELECT id, registration_id, failure_code, failure_reason,
       retry_count, next_retry_time, last_retry_time, status, create_time
FROM tb_registration_dead_letter
WHERE status IN ('PENDING', 'AUTO_RETRYING', 'MANUAL_REQUIRED')
ORDER BY create_time ASC;
```

按状态和失败码汇总：

```sql
SELECT status, failure_code, COUNT(*) AS total,
       MIN(create_time) AS oldest_time
FROM tb_registration_dead_letter
GROUP BY status, failure_code
ORDER BY oldest_time ASC;
```

## 主要配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REGISTRATION_DLQ_AUTO_RETRY_ENABLED` | `true` | 是否开启自动重试 |
| `REGISTRATION_DLQ_AUTO_RETRY_MAX_ATTEMPTS` | `3` | 单条死信最多自动重放次数 |
| `REGISTRATION_DLQ_AUTO_RETRY_BACKOFF_SECONDS` | `60` | 首次退避秒数 |
| `REGISTRATION_DLQ_AUTO_RETRY_MAX_BACKOFF_SECONDS` | `3600` | 最大退避秒数 |
| `REGISTRATION_DLQ_AUTO_RETRY_CLAIM_TIMEOUT_SECONDS` | `300` | 任务卡死后的记录回收时间 |
| `REGISTRATION_DLQ_AUTO_RETRY_FAILURE_CODES` | `RETRY_EXHAUSTED` | 允许自动重放的失败码，逗号分隔 |
| `REGISTRATION_DLQ_ALERT_WEBHOOK_URL` | 空 | 转人工时发送 JSON 告警的 Webhook 地址 |

未配置 `REGISTRATION_DLQ_ALERT_WEBHOOK_URL` 时，系统仍会记录错误日志，但不会主动推送外部消息。

## Webhook 告警格式

```json
{
  "event": "REGISTRATION_DEAD_LETTER_MANUAL_REQUIRED",
  "deadLetterId": 123,
  "registrationId": 10001,
  "failureCode": "RETRY_EXHAUSTED",
  "failureReason": "database timeout",
  "autoRetryAttempts": 3,
  "manualReason": "database timeout",
  "occurredAt": "2026-09-22T17:30:00",
  "adminPath": "/admin/registration/dead-letters"
}
```

Webhook 可以是企业网关、消息队列转发服务或自建通知服务；失败不会阻塞死信主流程，会输出错误日志。

## Prometheus 指标

```text
registration_dead_letter_pending
registration_dead_letter_manual_required
registration_dead_letter_oldest_pending_seconds
registration_dead_letter_auto_replayed_total
registration_dead_letter_auto_retry_failed_total
registration_dead_letter_manual_required_total
```

建议告警规则：

```yaml
groups:
  - name: campus-pilot-dead-letter
    rules:
      - alert: RegistrationDeadLetterPending
        expr: registration_dead_letter_pending > 0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "报名死信持续待处理"
          description: "当前有 {{ $value }} 条死信等待自动处理。"

      - alert: RegistrationDeadLetterManualRequired
        expr: registration_dead_letter_manual_required > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "报名死信需要人工处理"
          description: "当前有 {{ $value }} 条死信需要人工确认。"

      - alert: RegistrationDeadLetterOldestPendingTooLong
        expr: registration_dead_letter_oldest_pending_seconds > 600
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "报名死信积压时间过长"
          description: "最老待处理死信已经等待 {{ $value }} 秒。"
