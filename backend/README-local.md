# CampusPilot 本地启动说明

## 推荐方式

在 backend 目录执行：

```powershell
.\start-local.ps1
```

脚本会：

- 在 `backend/.env.local` 生成本机专用的强随机密钥；
- 同步 Java 与 Python 共用的内部令牌；
- 启用 Spring `local` 配置；
- 关闭本机未运行的 RocketMQ 消费者。

然后在另一个终端启动助手：

```powershell
cd C:\Users\manba\Desktop\campus-pilot\assistant-service
.\start.ps1
```

## 数据库迁移

智能客服转人工使用独立工单表，首次部署执行：

```text
src/main/resources/db/assistant_support_ticket_migration.sql
```

## 生产配置

生产环境不能使用 `.env.local`。必须在部署平台注入：

```dotenv
ASSISTANT_ENABLED=true
ASSISTANT_INTERNAL_TOKEN=<强随机令牌>
ASSISTANT_USER_CONTEXT_SECRET=<仅供 Java 使用的独立强随机令牌>
ASSISTANT_SERVICE_URL=http://assistant-service:8011
ASSISTANT_READ_TIMEOUT_MS=35000
ASSISTANT_RATE_LIMIT_PER_MINUTE=20
```

Python 侧必须配置相同的 `ASSISTANT_INTERNAL_TOKEN`。默认配置会关闭助手入口，
缺少密钥或使用示例密钥时，启用助手会直接拒绝启动。

## 数据服务

默认本地地址：

```text
MySQL: 127.0.0.1:3306/campus_pilot
Redis: 127.0.0.1:6379
```

数据库连接可通过以下环境变量覆盖：

```dotenv
CAMPUS_DB_URL=jdbc:mysql://127.0.0.1:3306/campus_pilot?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
CAMPUS_DB_USERNAME=root
CAMPUS_DB_PASSWORD=
```
