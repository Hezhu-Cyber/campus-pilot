# 智能客服 Agent 架构设计

## 1. 目标

这个项目的目标是把 LLM 接入真实业务，同时避免以下风险：

- 模型编造活动、报名状态或执行结果。
- 模型误报名、误取消或选择错误活动。
- 网络超时造成重复执行或状态不明。
- Java 和 Python 服务之间用户身份被伪造。
- 会话历史导致单机有状态、重启丢失和内存无限增长。
- 工具输出或用户输入中的提示词注入影响系统规则。

## 2. 总体架构

浏览器 -> Vue 前端 -> Spring Boot 助手网关 -> FastAPI LangGraph Agent
FastAPI Agent -> 只读工具 -> Spring Boot 内部接口
Spring Boot -> Redis / MySQL
Spring Boot -> 确认动作状态机
Python -> 外部 LLM
Python -> 离线评测器

## 3. 请求链路

1. 浏览器携带登录令牌调用 Java /assistant/chat。
2. Java 校验登录、限流，并读取 Redis 中最近会话历史。
3. Java 签发短期 HMAC 用户上下文，发送给 Python。
4. Python 先执行提示词注入检测和确定性动作路由。
5. 普通查询进入只读 LLM Agent；报名和取消进入确定性动作流程。
6. Python 使用签名上下文调用 Java 内部工具接口。
7. Java 根据签名上下文恢复当前用户，不信任请求头中的用户 ID。
8. Python 返回结构化回答。
9. Java 保存本轮问答历史并返回浏览器。

## 4. 为什么拆分读和写

LLM 适合理解自然语言和编排只读工具，不适合直接控制高风险事务。

- 读操作：调用工具失败或回答不准确的影响可恢复。
- 写操作：重复执行、目标选择错误或状态不明会造成真实业务损失。

因此写操作采用：

明确意图识别 -> 查询并唯一确定业务对象 -> 生成一次性确认动作 -> 用户点击确认 -> 执行前二次校验 -> 幂等执行 -> 状态可查询。

## 5. 确认动作状态机

PENDING -> PROCESSING -> SUCCESS
PENDING -> EXPIRED
PROCESSING -> CANCELLED
PROCESSING -> FAILED_RETRYABLE -> PROCESSING
PROCESSING -> FAILED

状态保存在 Redis，并绑定当前用户。确认令牌是 256 位随机值，不能跨用户使用。

## 6. 身份安全

Java 使用只有自己持有的独立密钥签发上下文：

base64url(payload).base64url(HMAC-SHA256(payload))

Payload 包含 userId、role、issuedAt 和 expiresAt。

Python 不持有签名密钥，只透传上下文。即使 Python 服务被利用，也无法自行构造其他用户身份。

## 7. 会话设计

- Java 是会话历史唯一写入方。
- Redis 使用 List 保存消息，限制最大条数和 TTL。
- Python 不保存跨请求状态。
- 同一用户同一会话通过 Redisson 锁串行化，避免上下文交错。

## 8. 可靠性设计

- 模型单次超时：12 秒。
- 请求总超时：30 秒。
- Java 上游等待：35 秒。
- 最大工具轮次：2。
- 模型失败：只读请求降级到规则模式。
- 工具失败：明确返回数据不可用。
- 确认执行：Redis 锁 + 幂等状态 + 执行前业务校验。
- 限量报名：轮询 Java 报名状态并更新动作状态。

## 9. 可观测性

Java：

- assistant.chat.requests
- assistant.chat.latency
- 与既有报名指标和 Actuator 指标统一由 Prometheus 抓取。

Python：

- assistant_chat_requests_total
- assistant_chat_latency_seconds
- assistant_tool_calls_total
- assistant_agent_fallbacks_total

## 10. Agent 评测

评测集位于 assistant-service/evals/cases.jsonl。

覆盖：

- 意图路由
- 读写动作边界
- 转人工识别
- 提示词注入
- 内部信息泄露
- 未执行动作的虚假陈述
- PII 脱敏

离线评测可以在 CI 中稳定运行。设置 EVAL_AUTH_TOKEN 后，可以通过 python evals/runner.py --live 对完整链路测延迟和成功率。

## 11. 主要取舍

### 为什么不让模型直接调用写工具

安全边界比多轮自然语言体验更重要。牺牲一部分自主性，换取事务安全、可审计和可恢复。

### 为什么用 Redis 而不是 Python Checkpointer

现有业务已经依赖 Redis。把会话和确认状态放到 Java/Redis 一侧，可以避免 Python 单机状态，并支持多实例。

### 为什么保留规则降级

模型不可用时，活动查询和报名记录仍然是核心能力。规则降级可以避免整个客服入口不可用。

### 为什么需要执行前二次校验

确认令牌有 5 分钟有效期，活动状态和库存可能在确认期间变化。执行前重新校验是最后的业务防线。
