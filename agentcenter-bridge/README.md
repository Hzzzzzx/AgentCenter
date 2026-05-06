# AgentCenter Bridge

Java Spring Boot 后端。管理工作事项、工作流、会话、确认项，通过 opencode serve 提供真实 AI 对话。

## Tech Stack

- Java 17
- Spring Boot 3.4.5
- MyBatis 3.0.4
- SQLite（开发）/ PostgreSQL（生产）
- Flyway 8.5.13

## Prerequisites

- Java 17+
- Maven 3.8+（或用自带的 `./mvnw`）
- opencode CLI（`npm install -g opencode-ai`），需先 `opencode auth` 登录

## Run

```bash
./mvnw spring-boot:run
```

启动后监听 8080 端口。首次发消息时会自动启动 `opencode serve`（4097 端口）。

## Build & Test

```bash
./mvnw clean package    # 编译打包
./mvnw test             # 跑测试
```

## API Endpoints

### Work Items

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/work-items | 列出所有事项 |
| POST | /api/work-items | 创建事项 |
| PUT | /api/work-items/{id} | 更新事项 |
| POST | /api/work-items/{id}/start-workflow | 启动工作流 |

### Workflow

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/workflow-definitions | 工作流定义列表 |
| GET | /api/workflow-instances/{id} | 工作流实例详情 |
| POST | /api/workflow-instances/{id}/continue | 继续工作流 |
| POST | /api/workflow-node-instances/{id}/retry | 重试失败节点 |
| POST | /api/workflow-node-instances/{id}/skip | 跳过节点 |

### Agent Sessions（AI 对话）

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/agent-sessions | 会话列表 |
| POST | /api/agent-sessions | 创建会话（runtimeType=OPENCODE） |
| GET | /api/agent-sessions/{id}/messages | 获取消息历史 |
| POST | /api/agent-sessions/{id}/messages | 发送消息（触发 AI 回复） |
| GET | /api/agent-sessions/{id}/events | **SSE 事件流**（AI 回复通过这里推送） |

### Confirmations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/confirmations | 确认项列表 |
| POST | /api/confirmations/{id}/resolve | 通过确认 |
| POST | /api/confirmations/{id}/reject | 拒绝确认 |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /health | 健康检查 |

## 数据流

```
浏览器 → POST /api/agent-sessions/{id}/messages → Java Bridge → POST /session/{id}/prompt_async → opencode serve
浏览器 ← SSE /api/agent-sessions/{id}/events ← Java Bridge ← SSE /event ← opencode serve
```

- **发消息**：REST POST
- **收回复**：SSE 事件流（ASSISTANT_DELTA 类型）
- 一个 agent_session 对应一个 opencode session，对话是连续的

## Configuration

配置文件：`src/main/resources/application.yml`

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `server.port` | 8080 | Bridge 端口 |
| `agentcenter.runtime.opencode.serve.enabled` | true | 是否启用 opencode 适配器 |
| `agentcenter.runtime.opencode.serve.port` | 4097 | opencode serve 端口 |
| `agentcenter.runtime.opencode.serve.working-directory` | `${user.dir}/..` | opencode 工作目录 |

## Database

SQLite 文件在 `./data/agentcenter.db`，Flyway 管理 schema 迁移（`src/main/resources/db/migration/`）。

## Architecture

DDD 分层：

```
Controller       → API 入口、参数校验
Application      → 业务编排
Domain           → 领域规则和状态
Infrastructure   → 持久化（MyBatis）、运行时适配器（opencode）
```

## Project Structure

```
agentcenter-bridge/
├── pom.xml
├── src/main/java/com/agentcenter/bridge/
│   ├── AgentCenterBridgeApplication.java
│   ├── api/                          # REST Controllers
│   ├── application/                  # 业务编排 Service
│   ├── domain/                       # 领域对象
│   │   ├── workitem/
│   │   ├── workflow/
│   │   ├── session/
│   │   ├── confirmation/
│   │   └── runtime/
│   ├── infrastructure/
│   │   ├── persistence/              # MyBatis Mapper
│   │   ├── runtime/
│   │   │   ├── mock/                 # Mock 适配器（开发回退）
│   │   │   └── opencode/             # 真实 opencode 适配器
│   │   │       ├── OpenCodeRuntimeAdapter.java      # REST 客户端
│   │   │       ├── OpenCodeProcessManager.java      # 进程管理
│   │   │       └── OpenCodeEventSubscriber.java     # SSE 事件消费
│   │   └── id/                       # ID 生成（UUID）
│   └── worker/
├── src/main/resources/
│   ├── application.yml
│   ├── mapper/                       # MyBatis XML
│   └── db/migration/                 # Flyway SQL
└── src/test/
```

## 相关文档

- [M1 Runbook](../docs/architecture/AGENT-RUNTIME-BRIDGE-M1-RUNBOOK.md) — 完整启动指南和故障排查
- [ADR-001](../docs/architecture/ADR-001-OPENCODE-BRIDGE-SSE-REST.md) — 架构决策记录
