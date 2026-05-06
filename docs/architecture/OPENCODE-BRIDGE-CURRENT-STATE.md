# OpenCode Bridge 当前状态说明

> 状态：现状交接（M1 实施中，决策见 [ADR-001](./ADR-001-OPENCODE-BRIDGE-SSE-REST.md)）
> 最近更新：2026-05-06
> ⚠️ M1 决策：采用 REST+SSE 方案，走 Java SSE 基础设施，不走 WebSocket 改造
> 目的：让后续接手的 OpenCode 清楚当前做到哪里、哪里是错路、哪里必须重做

## 1. 当前结论

当前项目还没有真正串通 OpenCode 实时对话。

已经有 Vue 工作台、Java Bridge、SQLite 表、WebSocket 入口和一部分会话/工作流/待确认模型，但真实 OpenCode Runtime 没有接入。现在的代码仍然大量残留 Mock 路径，网页无法稳定展示大模型真实输出。

需要接手者优先按 [OPENCODE-BRIDGE-TARGET-STATE.md](./OPENCODE-BRIDGE-TARGET-STATE.md) 重做运行时对接，不要继续沿用 Mock 或一次性 `opencode run` 方向。

## 2. 已经存在的工程

### 2.1 前端

目录：

```text
agentcenter-web/
```

当前技术栈：

- Vue 3
- Vite
- Pinia
- WebSocket client

主要文件：

```text
agentcenter-web/src/App.vue
agentcenter-web/src/components/shell/AppShell.vue
agentcenter-web/src/components/shell/LeftSidebar.vue
agentcenter-web/src/components/shell/RightPanel.vue
agentcenter-web/src/views/HomeOverview.vue
agentcenter-web/src/views/BoardView.vue
agentcenter-web/src/views/WorkflowConfig.vue
agentcenter-web/src/views/ConversationWorkbench.vue
agentcenter-web/src/stores/runtime.ts
agentcenter-web/src/stores/sessions.ts
```

已经具备：

- 首页、看板、工作流、对话工作台的 Vue 页面骨架。
- 左侧通用会话/任务会话列表。
- 右侧“待确认”和“详情”面板。
- `runtime.ts` 已创建 WebSocket 到 `/ws/agent-sessions/{sessionId}`。
- `ConversationWorkbench.vue` 有输入框、发送按钮、场景 chips 和消息列表。

主要问题：

- `App.vue` 创建通用会话时仍有 `runtimeType: 'MOCK'`。
- `ConversationWorkbench.vue` 创建任务会话时仍有 `runtimeType: 'MOCK'`。
- 前端目前只消费 `session.messages` 快照，没有完整处理 assistant delta、tool、skill、confirmation 等事件。
- 对话区是否能显示真实模型回复完全依赖后端是否写入 `agent_message`，但后端目前没有真实流式写入。

## 3. Java Bridge 当前状态

目录：

```text
agentcenter-bridge/
```

当前技术栈：

- Spring Boot 3.3.6
- Java 当前本机是 17，目标应切到 JDK 21
- MyBatis
- SQLite
- Spring WebSocket

主要文件：

```text
agentcenter-bridge/src/main/java/com/agentcenter/bridge/api/AgentSessionController.java
agentcenter-bridge/src/main/java/com/agentcenter/bridge/api/websocket/SessionWebSocketHandler.java
agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/AgentSessionService.java
agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/WorkflowCommandService.java
agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/runtime/AgentRuntimeAdapter.java
agentcenter-bridge/src/main/java/com/agentcenter/bridge/infrastructure/runtime/mock/MockRuntimeAdapter.java
agentcenter-bridge/src/main/resources/db/migration/V1__create_m1_schema.sql
agentcenter-bridge/src/main/resources/db/migration/V2__seed_m1_data.sql
```

已经具备：

- `agent_session`、`agent_message`、`runtime_event`、`workflow_*`、`confirmation_request` 等表。
- `AgentSessionController` 的会话 CRUD 和消息接口。
- `SessionWebSocketHandler` 的 WebSocket endpoint。
- `WebSocketSessionRegistry` 能按 `agentSessionId` 推送消息。
- `RuntimeEventService` 已可持久化 runtime event 并通过 WebSocket 广播。
- `WorkflowCommandService` 已有 FE 工作流实例和待确认雏形。

主要问题：

- 当前真正实现的 `AgentRuntimeAdapter` 只有 `MockRuntimeAdapter`。
- `AgentRuntimeAdapter.sendMessage()` 是 `void`，没有返回或流式 callback 设计。
- `AgentSessionService.sendMessage()` 只可靠地写入 USER 消息；Mock 路径才补一条固定 assistant 消息。
- 工作流路径仍使用 `RuntimeType.MOCK`。
- OpenCode session、OpenCode event stream、OpenCode skill/tool 事件都没有真实接入。
- 健康检查和默认 runtime 配置曾被临时调整过，不能代表真实对接完成。

## 4. 临时错误方向说明

本次过程中曾临时加入过基于 `opencode run` 的 Java 调用思路，相关文件包括：

```text
agentcenter-bridge/src/main/java/com/agentcenter/bridge/infrastructure/runtime/opencode/OpenCodeCliClient.java
agentcenter-bridge/src/main/java/com/agentcenter/bridge/infrastructure/runtime/opencode/OpenCodeChatResult.java
```

这条路不是目标方案。

它的问题：

- `opencode run` 是一次性命令调用，不是常驻实时会话。
- 即使加 `--session`，仍然是每条消息启动一次进程。
- 无法稳定承载 token streaming、tool event、permission asked、skill started/completed。
- 无法支撑右侧“待确认”与工作流节点实时联动。

接手实现时建议：

- 删除或重写这些临时代码。
- 保留最多作为 `SmokeTestOpenCodeCli` 之类的诊断工具，不进入主业务链路。
- 主链路改为常驻 OpenCode runtime adapter。

## 5. 当前 WebSocket 链路

当前链路：

```text
Vue ConversationWorkbench
  -> runtimeStore.sendUserMessage()
  -> WebSocket /ws/agent-sessions/{agentSessionId}
  -> SessionWebSocketHandler.handleUserMessage()
  -> AgentSessionService.sendMessage()
  -> DB agent_message
  -> session.messages snapshot
  -> Vue MessageList
```

这个链路的问题：

- WebSocket 通了，但只通到 Java Bridge。
- Java Bridge 没有真正连上 OpenCode 常驻会话。
- 没有 assistant streaming。
- 没有 tool/skill 事件转换。
- 没有 OpenCode permission/confirmation 转换。

因此用户在网页输入后，看不到真实大模型响应，是符合当前代码状态的。

## 6. 当前 Mock 残留点

需要优先清理或隔离：

```text
agentcenter-web/src/App.vue
  runtimeType: 'MOCK'

agentcenter-web/src/views/ConversationWorkbench.vue
  runtimeType: 'MOCK'

agentcenter-bridge/src/main/java/com/agentcenter/bridge/infrastructure/runtime/mock/MockRuntimeAdapter.java
  当前唯一完整 adapter

agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/WorkflowCommandService.java
  RuntimeType.MOCK
  confirmation.setRuntimeType(RuntimeType.MOCK.name())

agentcenter-bridge/src/main/resources/db/migration/V1__create_m1_schema.sql
  agent_session.runtime_type 默认值为 MOCK
```

目标：

- 生产和开发默认都应走 `OPENCODE`。
- Mock 只允许 test profile 或显式 mock profile 使用。
- UI 不应该把 Mock 当作默认路径。

## 7. 已验证的信息

本机存在 `opencode` CLI：

```text
/Users/hzz/.nvm/versions/node/v24.13.0/bin/opencode
```

`opencode providers list` 显示本机已有多个 provider credential。

`opencode run --format json` 能返回 JSON event，并能输出模型文本。这只证明 OpenCode CLI 可用，不证明产品链路已接通。

需要注意：

- 这个验证不是最终方案。
- 目标实现必须基于 `opencode serve` 或 OpenCode 常驻 API。
- 接手者应先写一个 OpenCode Runtime contract test，固定当前版本 OpenCode 的真实 API。

## 8. 应该交给 OpenCode 继续做的任务

### T1：明确 OpenCode headless API

运行并确认：

```bash
opencode serve --help
opencode serve --hostname 127.0.0.1 --port 4096
```

然后用 curl 或 Java client 验证：

- 如何创建 session。
- 如何发送 prompt 到指定 session。
- 如何订阅 session/global event stream。
- tool/skill/permission/error 事件的 JSON 结构。
- 是否支持 cancel。

参考文档：

```text
docs/prototype/opencode-bridge.md
```

### T2：实现 `OpenCodeRuntimeAdapter`

不要再走 `opencode run`。

应实现：

```text
createSession()
sendMessage()
subscribeEvents()
runSkill()
cancel()
```

并让事件进入统一 translator。

### T3：重构消息写入

当前 `AgentRuntimeAdapter.sendMessage()` 是 `void`，需要改成异步/事件驱动模型：

```text
USER message persisted
assistant message started
assistant delta appended
assistant completed persisted
tool messages persisted
runtime events persisted
```

### T4：前端消费实时事件

`runtime.ts` 需要处理：

```text
message.user.accepted
message.assistant.started
message.assistant.delta
message.assistant.completed
tool.started
tool.completed
skill.started
skill.completed
confirmation.created
workflow.node.updated
runtime.error
session.snapshot
```

### T5：清理 Mock 默认路径

- 前端创建会话默认 `OPENCODE`。
- Java 默认 runtime `OPENCODE`。
- DB seed 和 migration 明确只在 test profile 走 Mock。
- Health endpoint 真实检查 OpenCode runtime，而不是写死字符串。

### T6：工作流和待确认串起来

- FE1234 点击“开始”触发工作流。
- 节点按顺序调用 OpenCode skill。
- 节点输出 Markdown artifact。
- 需要用户确认时生成 `confirmation_request`。
- “待确认”点击“处理”进入绑定会话并可继续工作流。

## 9. 验收命令和验收场景

实现完成后必须至少跑通：

```bash
cd agentcenter-bridge
./mvnw test
```

```bash
cd agentcenter-web
npm run typecheck
npm test
```

浏览器验收：

1. 打开 `http://127.0.0.1:5173/`。
2. 新建通用会话。
3. 输入“请用一句话说明当前 OpenCode 已连接”。
4. 页面出现用户消息。
5. 页面实时出现 OpenCode 回复，而不是固定 Mock 文案。
6. 左侧出现该会话。
7. 刷新页面后消息仍在。
8. 第二轮输入能沿用同一个 OpenCode session。
9. 点击 FE1234 “开始”，能进入任务会话并展示工作流事件。
10. 右侧待确认“处理”能进入对应会话。

## 10. 接手提醒

最重要的判断标准很简单：

```text
如果网页对话输出不是来自 OpenCode 常驻 session，就还没有串通。
```

不要被以下状态误导：

- WebSocket 显示已连接。
- Java health 返回 ok。
- DB 里有用户消息。
- Mock assistant 出现固定文案。
- `opencode run` 在命令行能返回文本。

这些都不是最终验收。最终验收只看网页是否能通过 Java Bridge 实时展示同一个 OpenCode session 的真实输出。

