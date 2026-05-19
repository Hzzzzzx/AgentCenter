# Workflow State Retention 1026 实施计划

> 状态：待实施计划
> 分析分支：`codex/from-2026-05-14-1026`
> 计划时间：2026-05-19
> 对应设计：[docs/architecture/WORKFLOW-STATE-RETENTION-CLOSURE-1026.md](../../docs/architecture/WORKFLOW-STATE-RETENTION-CLOSURE-1026.md)

## 目标

在 1026 分支上补齐 OpenCode 上下文压缩后的工作流状态保持闭环，确保：

- 用户继续、补充、追问当前节点时，Bridge 每次都重新注入权威状态。
- Runtime 运行进度进入 `runtime_operation` 账本。
- Bridge 能在压缩、权限等待、超时、服务重启后恢复当前节点。
- OpenCode hook 只做压缩摘要增强，不成为状态源。

## 分支说明

本计划基于 `codex/from-2026-05-14-1026` 的代码分析整理。其他 Agent 如果已经在后续分支引入新提交，需要先对比当前计划中的缺口是否已被修复，不要直接把本计划当作最新主干状态。

已确认的 1026 关键现状：

- `AgentSessionService.continueCurrentRuntime(...)` 仍存在裸发 runtime prompt 路径。
- `WorkflowCommandService.executeSkillOnNode(...)` 是完整上下文和 `AGENTCENTER_RESUME_STATE` 注入入口。
- `runtime_operation` 表和 `RuntimeOperationType.SKILL_RUN` 已存在，但 `DefaultRuntimeGateway.runSkill(...)` 尚未创建 `skill.run` operation。
- `workflow_node_instance.agent_state_payload_json` 结果返回后才写入，派发前没有 last invocation payload。
- Runtime event listener 当前主要用于 UI 投影和 confirmation 创建，未形成 operation/node 恢复闭环。

## 实施分级

该任务涉及 Bridge API、工作流状态、Runtime Adapter、权限确认、上下文压缩和前端诊断，按 AgentCenter SDD 属于 L3/L4 边界。建议分批提交，每批都带定向测试。

## Phase 1：统一继续路径

目标：消除 `CONTINUE_CURRENT` 裸发 prompt。

改动：

1. 修改 `AgentSessionService.continueCurrentRuntime(...)`：
   - 只要存在 `nodeInstanceId`，一律调用 `workflowCommandService.resumeNodeAfterInteraction(nodeInstanceId, continueCurrentPrompt(requestedPrompt))`。
   - 找不到 node 时才 fallback 到 `dispatchToRuntime(...)`，并发布 warning/diagnostic event。
2. 更新 `WorkflowMidSessionInputRoutingIntegrationTest`：
   - 删除或重写 `continueCurrent_sendsRuntimeContinueWithoutReplayingNodePrompt`。
   - 增加 `continueCurrent_alwaysResumesWithFullNodeContext`。
3. 确认 typed input、继续按钮、暂停后继续都走同一路径。

验证：

```bash
cd agentcenter-bridge
./mvnw -Dtest=WorkflowMidSessionInputRoutingIntegrationTest test
```

验收：

- `CONTINUE_CURRENT` 后最新 captured input context 包含 `## AGENTCENTER_RESUME_STATE`。
- 即使没有 compaction event，也包含当前节点、上游产物和用户本轮输入。

## Phase 2：派发前节点 payload

目标：DB 在 runtime 返回前就能看到 last invocation。

改动：

1. 在 `WorkflowCommandService.executeSkillOnNode(...)` 生成 `resumeState` 后、调用 `runtimeGateway.runSkill(...)` 前，写入：

```json
{
  "phase": "DISPATCHING",
  "invocationId": "...",
  "runtimeOperationId": null,
  "currentGate": "NODE_EXECUTION",
  "workflowInstanceId": "...",
  "workflowNodeInstanceId": "...",
  "runtimeSessionId": "...",
  "skillName": "...",
  "pendingInteractionIds": [],
  "dispatchedAt": "..."
}
```

2. 结果回来后保留同一个 `invocationId`，更新 phase：
   - `COMPLETED`
   - `WAITING_USER`
   - `FAILED`
   - `REJECTED_TRANSITION`

验证：

```bash
cd agentcenter-bridge
./mvnw -Dtest=WorkflowMidSessionInputRoutingIntegrationTest test
```

验收：

- runtime 返回前查询 `workflow_node_instance.agent_state_payload_json` 可见 `phase=DISPATCHING`。
- 结果返回后 payload 保留同一 `invocationId`。

## Phase 3：WorkflowStateSnapshotService

目标：把 DB 权威状态投影为 Runtime 可读 JSON/Markdown。

改动：

1. 新增 `WorkflowStateSnapshotService`。
2. 输出文件：

```text
runtime-workspace/.agentcenter/state/{runtimeSessionId}.json
runtime-workspace/.agentcenter/state/{runtimeSessionId}.md
```

3. Snapshot 内容包含：
   - schemaVersion
   - snapshotVersion
   - stateHash
   - workItem
   - workflowInstanceId
   - currentNodeInstanceId
   - currentGate
   - nodeStatus
   - skillName
   - invocationId
   - runtimeOperationId
   - runtimeSessionId
   - workflow steps
   - pending interactions
   - upstream artifacts 摘要
   - recovery rule
4. 刷新时机：
   - workflow 启动
   - node 派发前
   - node 状态变化后
   - confirmation 创建/解决后
   - compaction event 后
   - Bridge 启动恢复扫描后

验证：

```bash
cd agentcenter-bridge
./mvnw -Dtest=WorkflowStateSnapshotServiceTest test
```

验收：

- Snapshot 可删除、可重建。
- Snapshot 文件只由 DB 投影生成，不反向驱动业务状态。
- Markdown 中明确“如果历史对话或压缩摘要冲突，以本快照为准”。

## Phase 4：`skill.run` operation 账本

目标：每次 workflow skill 调用都有 runtime operation。

改动：

1. 新增 `RuntimeOperationContext` 或扩展 `SkillInvocationRequest` 元数据。
2. 在 workflow 调用 `runtimeGateway.runSkill(...)` 前创建 operation：
   - `operationType=skill.run`
   - `resourceType=skill`
   - `resourceId=skillName`
   - `correlationId=invocationId`
   - `agentSessionId`
   - `runtimeSessionId`
   - `workItemId`
   - `workflowInstanceId`
   - `workflowNodeInstanceId`
   - `commandJson`
   - `deadlineAt`
3. 派发后更新：
   - `CREATED -> DISPATCHING`
   - ack 成功：`ACCEPTED`
   - event running：`IN_PROGRESS`
   - result success：`SUCCEEDED`
   - transport/runtime error：`FAILED`
   - watchdog timeout：`TIMED_OUT`
4. 将 `runtimeOperationId` 写回 node payload 和 snapshot。

验证：

```bash
cd agentcenter-bridge
./mvnw -Dtest=RuntimeOperationServiceTest,WorkflowMidSessionInputRoutingIntegrationTest test
```

验收：

- 可按 workflow/node/session 查到 `skill.run` operation。
- operation 与 `invocationId` 一一对应。
- 失败、超时、取消都有终态。

## Phase 5：Runtime event 绑定和权限恢复

目标：permission/question/tool/compaction event 都能绑定 workflow/node/operation。

改动：

1. `OpenCodeRuntimeEventTranslator.translatePermission(...)` 使用 context envelope。
2. `PermissionConfirmationHandler.buildPermissionEntity(...)` 写入：
   - workItemId
   - workflowInstanceId
   - workflowNodeInstanceId
   - runtimeOperationId
   - invocationId
3. `RuntimeEventEnvelope` 或 payload 扩展 operation/correlation 字段。
4. 用户批准 permission 后：
   - 能定位 node：回 `resumeNodeAfterInteraction(...)`
   - 能定位 active operation：先修复 node 绑定再恢复
   - 不能定位：创建人工恢复确认，不裸发继续

验证：

```bash
cd agentcenter-bridge
./mvnw -Dtest=OpenCodeRuntimeEventTranslatorTest,ConfirmationServiceTest test
```

验收：

- permission confirmation 带 workflow/node。
- 批准后当前节点恢复，不走普通 conversation dispatch。

## Phase 6：State-only repair

目标：正文完成但缺 `AGENTCENTER_NODE_STATE` 时自动补签。

改动：

1. 在 `WorkflowCommandService.executeSkillOnNode(...)` 解析结果后判断：
   - `result.success=true`
   - 输出正文非空
   - `WorkflowNodeStateParser.parse(...)` 返回默认 `No state block found`
2. 发起 state-only repair prompt：

```text
你上一轮已经输出当前节点内容，但缺少 AGENTCENTER_NODE_STATE。
请只根据上一轮输出和本轮 AGENTCENTER_RESUME_STATE 判断节点状态。
只返回一个 AGENTCENTER_NODE_STATE 注释块。
不要重新生成正文，不要推进其他节点，不要输出额外解释。
```

3. repair 成功：
   - 使用原始正文保存 artifact。
   - 使用补签状态推进或创建确认。
4. repair 失败：
   - 创建异常确认。
   - 节点不推进。
5. payload 标记：
   - `stateRepairOfInvocationId`
   - `stateRepairInvocationId`

验证：

```bash
cd agentcenter-bridge
./mvnw -Dtest=WorkflowNodeStateParserTest,WorkflowMidSessionInputRoutingIntegrationTest test
```

验收：

- repair 返回 `READY_TO_ADVANCE` 时保存原始正文，不保存 repair 正文。
- repair 再次缺协议时创建异常确认。

## Phase 7：Compaction hook 增强

目标：OpenCode 压缩摘要能带上 AgentCenter 当前状态。

改动：

1. 在 Runtime workspace 生成 `.opencode/plugins/agentcenter-compaction.ts` 或项目可控插件。
2. hook 监听 `experimental.session.compacting`。
3. hook 读取 `.agentcenter/state/{runtimeSessionId}.md`。
4. hook 追加到 `output.context`：

```text
当前 AgentCenter 工作流状态如下。若历史对话或压缩摘要与此冲突，以此为准。
...
```

5. hook 失败只记录日志，不阻塞 OpenCode。

验证：

- 插件加载 smoke test。
- 人工触发 compaction 后，process trace 中能看到 compaction；下一轮 prompt 仍能恢复。

验收：

- hook 成功时压缩摘要包含 current node 和 recovery rule。
- hook 失败时 Bridge 仍能通过 resume prompt 恢复。

## Phase 8：Watchdog 和 UI 诊断

目标：运行中断可见、可恢复、可解释。

改动：

1. 新增 operation watchdog：
   - 扫描 `CREATED/DISPATCHING/ACCEPTED/IN_PROGRESS`
   - 超过 `deadlineAt` 标记 `TIMED_OUT`
   - 创建 runtime recovery confirmation
   - 刷新 snapshot
2. 前端增加诊断折叠区：
   - currentNode
   - currentGate
   - invocationId
   - runtimeOperationId
   - operation status
   - latest compaction
   - latest context anchor
   - snapshotVersion/stateHash

验证：

```bash
cd agentcenter-bridge
./mvnw test

cd ../agentcenter-web
npm run typecheck
npm run test
```

UI 改动需要 Playwright 截图保存到 `.sisyphus/evidence/`。

## 风险和缓解

| 风险 | 缓解 |
|------|------|
| 改动 `CONTINUE_CURRENT` 影响用户轻量继续体验 | 通过定向测试证明 prompt 不重复展示给用户，只重新注入给 Runtime |
| operation 与同步 `runSkill` 状态竞争 | 用 `invocationId` 和终态保护，terminal operation 不再被旧 event 更新 |
| Snapshot 泄露敏感上下文 | 只写工作流状态、产物摘要和必要恢复规则，不写 secrets |
| hook API 变更 | hook 作为增强层，失败不影响 Bridge 主恢复 |
| Skill/MCP refresh 打断运行中 workflow | 活跃 operation 存在时标记 reloadRequired，节点结束后再重启 Runtime |

## 推荐提交顺序

1. `fix(workflow): route continue current through node resume`
2. `feat(workflow): persist dispatching node state payload`
3. `feat(workflow): add workflow state snapshots`
4. `feat(runtime): track workflow skill runs as operations`
5. `fix(runtime): bind permission events to workflow context`
6. `feat(workflow): repair missing node state blocks`
7. `feat(opencode): inject workflow snapshots during compaction`
8. `feat(runtime): add operation watchdog diagnostics`

## 完成定义

- 1026 分支中上下文压缩后，用户继续当前节点不会丢失当前 workflow/node。
- Bridge 重启后能从 DB + operation 恢复未完成节点状态。
- Snapshot 文件可辅助 OpenCode 压缩摘要，但删除后不影响业务恢复。
- Runtime permission/question/timeout 都能回到当前节点恢复。
- 缺协议输出不会静默卡死。
