# ADR-001: OpenCode Bridge M1 接入方案 — SSE+REST

> 状态：已决策，实施中
> 决策日期：2026-05-06
> 决策人：用户确认
> 影响范围：Java Bridge、Vue 工作台、OpenCode Runtime 对接

## 1. 决策结论

M1 阶段采用 **REST + SSE** 方案接入真实 OpenCode 对话，不走 WebSocket。

```text
Vue 前端
  ├── REST POST  /agent-sessions/{id}/messages     → 发送用户消息
  └── SSE GET    /api/agent-sessions/{id}/events   → 接收流式事件

Java Bridge (Spring Boot)
  ├── 收到用户消息 → HTTP POST → opencode serve /session/{id}/prompt_async
  ├── 消费 opencode serve /event SSE → 翻译为 RuntimeEvent
  └── 通过 SseEmitterRegistry 推 SSE 给前端

opencode serve (localhost, 常驻进程)
  └── 真实模型对话 + tool/skill/permission 事件
```

## 2. 决策理由

| 选项 | 分析 | 结论 |
|------|------|------|
| Vue WS → Java → opencode serve (WS) | opencode serve 不暴露 WebSocket，只暴露 SSE | ❌ 不可行 |
| Vue SSE → Java → opencode serve (SSE) | Java SSE 基础设施已有（SseEmitterRegistry + RuntimeEventStreamController），前端 events.ts + sseStream() 已写好 | ✅ 选定 |
| Vue WS → Java(WS→SSE转换) → opencode serve | 额外转换层，M1 不需要 | ⏳ 后续考虑 |
| Node Bridge 直连 | 绕过 Java Bridge，不符合企业控制面架构 | ❌ 不选 |

**核心考量**：
- Java Bridge 的 SSE 已经完整可用，不需要重写
- opencode serve 原生就是 SSE `/event`，Java 端消费 SSE 是自然对接
- 前端 `events.ts` 已经实现了 `sseStream()`，只是没激活
- 最快路径 = 激活已有代码 + 补 OpenCode adapter

## 3. 已有资产（不需要重写）

| 组件 | 文件 | 状态 |
|------|------|------|
| SSE endpoint | `RuntimeEventStreamController.java` | ✅ 可用 |
| SSE 推送 | `SseEmitterRegistry.java` | ✅ 可用 |
| 事件广播 | `RuntimeEventService.publishEvent()` | ✅ 可用，同时推 SSE + WS |
| 前端 SSE 消费 | `events.ts` + `client.ts::sseStream()` | ✅ 已写好，待激活 |
| 事件类型 | `RuntimeEventType` | ✅ ASSISTANT_DELTA 已定义 |
| opencode 事件翻译参考 | `tools/opencode-bridge.mjs::normalizeOpenCodeEvent()` | ✅ 可移植到 Java |

## 4. 需要实现的部分

### 4.1 Java: OpenCodeRuntimeAdapter

新建 `infrastructure/runtime/opencode/OpenCodeRuntimeAdapter.java`：
- 实现 `AgentRuntimeAdapter` 接口
- 内部 HTTP client 调 `opencode serve` REST API
- 后台线程消费 `opencode serve` 的 `/event` SSE
- 翻译事件为 `RuntimeEvent` → 调用 `RuntimeEventService.publishEvent()` 推给前端

关键方法：
```java
createSession(workItemId)   → POST opencode /session → 保存 runtime_session_id
sendMessage(sessionId, msg) → POST opencode /session/{id}/prompt_async
subscribe(sessionId)        → GET opencode /event SSE → 翻译 → publishEvent
cancel(sessionId)           → (M1 可选)
```

### 4.2 Java: OpenCode 进程管理

新建 `infrastructure/runtime/opencode/OpenCodeProcessManager.java`：
- 启动时检测/启动 `opencode serve --hostname 127.0.0.1 --port <configured>`
- 健康检查确认可用
- 进程挂掉时重连或报错

### 4.3 Java: AgentSessionService 路由修改

修改 `AgentSessionService.sendMessage()`：
- RuntimeType.OPENCODE → 调用 `OpenCodeRuntimeAdapter`（替换现在的 `OpenCodeCliClient`）
- 保持 session 连续：同一 agent_session 复用 `runtime_session_id`
- 首次发送时自动创建 opencode session

### 4.4 Frontend: 激活 SSE + 展示 delta

- `runtime.ts`：增加 SSE 连接逻辑，消费 `/api/agent-sessions/{id}/events`
- `ConversationWorkbench.vue`：处理 `ASSISTANT_DELTA` 事件，实时追加到对话区
- 发送消息：走 REST `POST /agent-sessions/{id}/messages`（已有）

### 4.5 Frontend: 清除 MOCK 默认

- `App.vue`：`runtimeType: 'MOCK'` → `'OPENCODE'`
- `ConversationWorkbench.vue`：同上

## 5. 会话连续性保证

**关键约束：不能一条消息创建一个新会话。**

```text
用户打开工作台 → 创建 AgentCenter session (agent_session)
                   ↓
            首次发消息时 → OpenCodeRuntimeAdapter.createSession()
                   ↓
            保存 runtime_session_id 到 agent_session.runtime_session_id
                   ↓
            后续消息 → 复用同一个 runtime_session_id → prompt_async
                   ↓
            同一个 opencode session 保持完整上下文
```

- `agent_session.runtime_session_id` 一旦创建，后续消息必须复用
- 只有 session 状态为 CLOSED 或 ERROR 时才允许新建 opencode session
- 前端不感知 opencode session id，只认 agent_session id

## 6. opencode serve API 实测结果

```text
opencode serve --hostname 127.0.0.1 --port 4097

POST /session
  Body: {"title":"xxx","permission":[{"permission":"edit","pattern":"*","action":"ask"}]}
  Header: x-opencode-directory: /path/to/project
  Response: {"id":"ses_xxx","slug":"xxx","version":"1.14.28",...}

POST /session/{id}/prompt_async
  Body: {"agent":"build","parts":[{"type":"text","text":"用户消息"}]}
  Header: x-opencode-directory: /path/to/project
  Response: HTTP 200/202

GET /event
  Header: x-opencode-directory: /path/to/project
  Response: SSE stream
  Event types: message.updated, message.part.delta, message.part.updated,
               session.status, session.idle, permission.asked, session.error
```

关键 header：`x-opencode-directory` 指定项目工作目录。

## 7. 事件翻译映射

opencode 原生事件 → AgentCenter RuntimeEvent：

| opencode 事件 | AgentCenter RuntimeEventType | SSE payload |
|---------------|------------------------------|-------------|
| `message.part.delta` (text) | ASSISTANT_DELTA | `{delta: "增量文本"}` |
| `message.part.updated` (text complete) | STATUS | assistant 完成标记 |
| `session.status` (busy) | STATUS | running |
| `session.idle` | STATUS | waiting_user |
| `permission.asked` | PERMISSION_REQUIRED | 确认请求 |
| `session.error` | ERROR | 错误信息 |
| tool call start/end | SKILL_STARTED / SKILL_COMPLETED | tool 信息 |

参考实现：`tools/opencode-bridge.mjs` 的 `normalizeOpenCodeEvent()` 函数。

## 8. M1 验收标准

1. 启动 Java Bridge + Vue 前端 + `opencode serve`
2. 打开 `http://localhost:5173`，新建通用会话
3. 输入一句话，页面立即出现用户消息（REST 返回）
4. 10 秒内 SSE 开始推送 assistant_delta，页面实时追加文本
5. 模型回复完成后，完整文本固化
6. 输入第二句，**复用同一个 opencode session**，上下文连续
7. 刷新页面后（M1 可选）消息仍在 DB

## 9. 后续（不在 M1 范围）

- WebSocket 作为 SSE 的替代传输（保留给 M2+）
- 消息持久化 + 刷新恢复
- 工作流节点串联 OpenCode skill
- 待确认 + permission 联动
- 取消正在进行的回复

## 10. 不做的事

- 不让前端直连 opencode serve
- 不用 `opencode run` CLI 作为正式链路
- 不用 Node Bridge 作为中间层
- 不在 M1 做 WebSocket 消息通道
- 不做 Mock 默认路径
