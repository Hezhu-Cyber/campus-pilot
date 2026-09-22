# CampusPilot 校园智能客服 Agent

一个面向校园活动场景的智能客服项目，覆盖活动查询、报名、取消报名、FAQ、转人工工单和管理员处理闭环。

项目重点不是“让大模型自由操作业务”，而是把 LLM Agent 放进可控、可观测、可恢复的工程边界内。

## 演示视频

在线观看：

https://github.com/Hezhu-Cyber/campus-pilot/blob/main/demo-video/CampusPilot-demo-final.mp4

仓库内位置：

    demo-video/CampusPilot-demo-final.mp4

中文字幕文件：

    demo-video/CampusPilot-demo.srt

视频时长约 65 秒，包含活动查询、提示词注入防护、报名确认、取消报名、转人工工单和管理员处理流程，并配有中文配音与字幕。

## 技术栈

- Java 17 / Spring Boot / MyBatis-Plus / Redisson
- Python 3.11 / FastAPI / LangChain / LangGraph
- Vue 3 / Vite / Element Plus
- MySQL 8 / Redis 7
- Prometheus / Docker Compose / GitHub Actions

## 核心设计

### 只读 Agent + 确定性写流程

LLM Agent 只能调用活动查询、报名记录、热门动态、报名方式和工单查询等只读工具。报名与取消报名由确定性路由和状态机处理，模型只负责识别明确意图，不能直接执行写操作。

### 可查询、可重试的确认动作

报名和取消先生成一次性确认动作。动作状态保存在 Redis：

PENDING -> PROCESSING -> SUCCESS / FAILED / FAILED_RETRYABLE / CANCELLED / EXPIRED

重复确认不会重复执行，网络超时后可以查询和恢复，执行前会再次校验活动状态、截止时间、容量和库存。

### 签名用户上下文

浏览器只访问 Java 网关。Java 为每次助手请求签发短期 HMAC 用户上下文，Python 仅透传。内部接口不信任普通用户 ID 请求头，避免 Python 服务或客户端伪造用户身份。

### 有界会话与故障降级

- 会话历史由 Java 存入 Redis，带条数限制和 TTL。
- Python 服务无跨请求内存状态，可水平扩容。
- 模型超时、工具轮次和总请求时间均有硬上限。
- 模型失败时，只读请求降级到确定性规则。
- 工具故障会明确返回数据不可用，不会伪装成没有数据。

### 安全与隐私

- 默认关闭助手入口，示例密钥和弱密钥会拒绝启动。
- Agent 工具结果标记为不可信数据，防止提示词注入。
- 最终回答拦截系统提示、内部令牌和确认令牌泄露。
- 手机号、邮箱、证件号在发送给外部模型前脱敏。
- 内部接口使用服务令牌和签名用户上下文双重校验。
- 按用户限流，并输出 Java/Python 两侧 Prometheus 指标。

## 快速启动

### Docker Compose

复制 .env.docker.example 为 .env.docker，修改密钥后执行：

docker compose --env-file .env.docker up --build

访问：

- 前端：http://127.0.0.1:8080
- Java API：http://127.0.0.1:8081
- Prometheus 指标：http://127.0.0.1:8081/actuator/prometheus

### 本地开发

在 backend 目录运行 start-local.ps1。

另一个终端在 assistant-service 目录运行 start.ps1。

首次启动数据库时执行：

- backend/src/main/resources/db/campus_pilot.sql
- backend/src/main/resources/db/rocketmq_registration_migration.sql
- backend/src/main/resources/db/assistant_support_ticket_migration.sql

## 本地演示账号

- 学生：13800000003
- 组织者：13800000002
- 管理员：13800000001

本地配置开启 EXPOSE_DEMO_CODE 后，点击发送验证码，页面会直接显示演示验证码。

## 测试与评测

Java：

    cd backend
    mvn -q test

Python：

    cd assistant-service
    .\.venv\Scripts\python.exe -m pytest -q
    .\.venv\Scripts\python.exe evals\runner.py

当前覆盖：

- Java：22 项自动化测试
- Python：16 项自动化测试
- Agent 离线评测：33 个场景，覆盖意图、动作安全、转人工、答案护栏和 PII 脱敏
- 前端：生产构建

## 端到端与压测

设置 E2E_AUTH_TOKEN 或 E2E_PHONE 后运行：

    python scripts\e2e_smoke.py

压测：

    $env:LOAD_AUTH_TOKEN = "<login token>"
    python scripts\load_test.py --requests 100 --concurrency 20

报告会写入 reports/e2e-latest.json 和 reports/load-latest.json。

## 实测数据

单机真实模型链路：

- Live Agent 评测 19 个场景，成功率 100%，P95 5.88 秒
- 100 请求、20 并发
- 成功率 100%
- 吞吐量 2.67 RPS
- 平均延迟 6.85 秒
- P50 6.72 秒
- P95 8.96 秒
- 最大延迟 10.76 秒

结果文件和限制说明见 docs/results.md。

## 目录结构

    backend/            Spring Boot 网关、业务、确认状态机、工单
    assistant-service/  FastAPI + LangGraph Agent、工具、评测
    frontend/           Vue 3 用户端和管理员工作台
    docs/               架构、评测和面试说明
    scripts/            端到端、压测和报名并发测试脚本

## 工程亮点

- 将高风险 AI 写操作从不可控工具调用改为可恢复状态机。
- 用签名用户上下文解决 Java/Python 跨服务身份可信问题。
- 用 Redis 同时解决会话一致性、幂等、锁和限流。
- 对模型输出、工具数据、内部信息和 PII 建立多层防护。
- 提供离线评测、端到端冒烟、压测和 CI，而不是只有功能代码。

详细设计见 docs/architecture.md。

实测数据和压测结果见 docs/results.md。
