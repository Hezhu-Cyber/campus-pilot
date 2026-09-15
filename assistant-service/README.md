# CampusPilot Assistant Service

CampusPilot 的独立智能助手服务，使用 FastAPI、LangChain 和 LangGraph。服务不直接访问 MySQL，
所有校园业务数据都通过 Spring Boot 的签名内部接口获取。

## 生产安全边界

- LLM Agent 只拥有只读工具，不能报名、取消报名、发帖或点赞。
- 报名和取消报名由确定性路由处理，模型只负责意图识别，不能直接执行写操作。
- 最终执行必须经过一次性确认；确认状态保存在 Redis，可查询、可安全重试。
- Java 为每次请求签发短期用户上下文，Python 仅透传，不能自行伪造用户身份。
- Python 启动时会拒绝空值或示例默认密钥。
- 用户请求按用户和会话串行化，并限制单用户请求频率。

## 本地启动

推荐在 backend 目录运行：

```powershell
.\start-local.ps1
```

脚本会生成仅用于本机的强随机密钥，同步 Python 的 `ASSISTANT_INTERNAL_TOKEN`，
并启动 Spring Boot。随后在另一个终端运行：

```powershell
cd C:\Users\manba\Desktop\campus-pilot\assistant-service
.\start.ps1
```

首次部署还需要执行：

```text
backend/src/main/resources/db/assistant_support_ticket_migration.sql
```

## 模型配置

`.env` 中的模型相关配置：

```dotenv
ASSISTANT_MODEL_BASE_URL=https://your-provider.example/v1
ASSISTANT_MODEL_API_KEY=your-api-key
ASSISTANT_MODEL_NAME=your-model-name
ASSISTANT_MODEL_TIMEOUT_SECONDS=12
```

本服务兼容 OpenAI、DeepSeek、通义千问和 Ollama 等 OpenAI 兼容接口。
模型不可用时，读请求会降级到确定性规则；代码只向外部模型发送完成当前问题所需的数据。

## 生产环境变量

Python：

```dotenv
ASSISTANT_JAVA_BASE_URL=http://127.0.0.1:8081
ASSISTANT_INTERNAL_TOKEN=<共享强随机令牌>
ASSISTANT_REQUEST_TIMEOUT_SECONDS=8
ASSISTANT_ASSISTANT_TIMEOUT_SECONDS=30
ASSISTANT_MODEL_TIMEOUT_SECONDS=12
```

Java：

```dotenv
ASSISTANT_ENABLED=true
ASSISTANT_INTERNAL_TOKEN=<与 Python 完全一致>
ASSISTANT_USER_CONTEXT_SECRET=<仅 Java 持有的独立强随机密钥>
ASSISTANT_READ_TIMEOUT_MS=35000
ASSISTANT_RATE_LIMIT_PER_MINUTE=20
```

生产环境应由密钥管理系统注入，不能把密钥写入镜像、Git 仓库或日志。

## 会话与故障恢复

会话历史由 Java 写入 Redis，设置条数和 TTL，因此重启或切换到其他 Python 实例不会丢上下文。
Python 本身无跨请求内存状态。总处理超时、单次模型超时和工具轮次均有硬上限。

## 当前能力

- 活动和活动分类查询
- 当前用户报名记录查询
- 热门校园动态查询
- 报名与取消报名的确认式执行
- 常见问题回答
- 明确要求人工客服时创建可追踪工单

如果数据库尚未创建 `tb_support_ticket`，转人工请求会返回服务不可用，不会谎报已经创建工单。

## Agent 评测与监控

离线评测：

    .\.venv\Scripts\python.exe evals\runner.py

评测集位于 evals/cases.jsonl，覆盖意图路由、动作安全、转人工、答案护栏和 PII 脱敏。

Prometheus 指标：

    http://127.0.0.1:8011/metrics

Live 评测需要先启动完整 Java/Python 链路，并设置 EVAL_AUTH_TOKEN：

    .\.venv\Scripts\python.exe evals\runner.py --live
