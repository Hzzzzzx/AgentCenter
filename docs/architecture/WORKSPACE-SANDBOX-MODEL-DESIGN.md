# AgentCenter 工作空间与运行沙盒设计

> 状态：1026 分支目标设计
> 最近更新：2026-05-25
> 分支：`codex/from-2026-05-14-1026`
> 范围：只覆盖协作空间、作业空间、运行沙盒、产物提升和实施计划；不包含长期记忆系统设计。

## 1. 设计结论

AgentCenter 当前应先固定工作空间和运行沙盒模型，再讨论更高层的上下文能力。

推荐结论：

> 工作空间模型按终局骨架设计，MVP 只实现单租户、默认 workspace、多项目、多用户、每次运行独立沙盒。

终局骨架：

```text
Tenant
  -> Workspace
    -> Project
      -> WorkItem
        -> Run
          -> RunSandbox

User
  -> UserProjectWorkspace
```

MVP 骨架：

```text
Tenant = default
Workspace = default
Project = existing project_id / project_context_id
WorkItem = existing work_item
Run = runtime_operation + workflow_node_instance + runtime_session_id
RunSandbox = per-run filesystem workspace
```

核心原则：

1. 每次 Agent 执行必须绑定 `user_id + project_id + work_item_id + run_id`。
2. 前端不传裸绝对路径，只传业务 ID，由 Bridge 解析工作空间。
3. Agent 默认只能写本次 `RunSandbox`。
4. 进入用户作业空间或项目共享空间必须通过确认或提升动作。
5. `runtime_operation` 是 1026 分支中承载一次 runtime 派发的运行账本，工作空间设计应先接到它上面。

## 2. 与 1026 分支的关系

1026 分支已有的主线是工作流状态保持：

```text
Bridge DB 权威状态
  -> WorkflowStateSnapshotService
  -> RuntimeOperation(skill.run)
  -> Runtime Event Listener
  -> Workflow Engine
  -> Notification / Confirmation / Artifact Projection
```

工作空间设计不替代这条主线，而是给它补上作用域和文件边界。

| 1026 现有对象 | 工作空间落点 | 说明 |
|---------------|--------------|------|
| `workflow_instance` | `Project + WorkItem` 下的流程实例 | 仍是工作流状态真源 |
| `workflow_node_instance` | 当前 Run 所在节点 | 节点状态、恢复 payload 和产物绑定点 |
| `runtime_operation` | 一次 Run 的派发账本 | 应记录 sandbox binding 和 correlation id |
| `runtime_session_id` | Runtime 会话 ID | 不能单独代表业务进度 |
| `runtime_event` | Run 过程事件 | 用于过程展示和审计 |
| `artifact` | Run 产物索引 | 文件真实内容来自受控工作空间 |
| `confirmation_request` | 产物提升、权限、异常和用户动作真源 | 不让前端直接改共享文件 |
| `notification_event` | 用户可见通知和卡片快照 | 展示为什么需要确认或处理 |

实施时不要先做一套新的运行模型，而应让现有 `runtime_operation`、`workflow_node_instance` 和 `artifact` 具备 workspace/sandbox 语义。

## 3. 空间分层

### 3.1 Collaboration Workspace

协作空间是业务边界，不是磁盘目录。

```text
Tenant / Organization
  -> Workspace
    -> Project
      -> WorkItem
      -> Workflow
      -> AgentSession
      -> Run
```

MVP：

- `Tenant` 固定为 `default`。
- `Workspace` 固定为 `default`，可映射到现有 `space_id` 或 `project_space_id`。
- `Project` 使用现有 `project_id` / `project_context_id`。
- `WorkItem` 继续使用当前 `work_item`。

后续：

- `Tenant` 对接企业组织。
- `Workspace` 承载团队、业务域或项目群。
- `Project` 成为默认数据隔离和工具授权边界。

### 3.2 Project Workspace

项目工作空间保存项目共享材料和已确认产物。

示例内容：

- 项目 repo 或 worktree。
- 项目 docs、架构材料、任务材料。
- 已确认的报告、设计文档、补丁和运行产物。

规则：

- Agent 默认不直接写项目共享区。
- 写入项目共享区必须来自 `ArtifactCandidate -> Confirmation -> Promotion`。
- 项目共享区可以被当前项目内的 run 读取。

### 3.3 User Project Workspace

用户项目作业空间保存某个用户在某个项目下的个人草稿和中间材料。

示例内容：

- 用户草稿。
- 未发布的分析材料。
- 用户确认保留但尚未项目共享的文件。
- 用户个人工具输出。

规则：

- 当前用户可写。
- 同项目其他用户默认不可读。
- 从 RunSandbox 提升到 UserProjectWorkspace 需要用户确认。

### 3.4 Run Sandbox

Run Sandbox 是某次 Agent 执行的临时隔离目录。

示例内容：

- 本轮 prompt 输入快照。
- 工具调用输出。
- 临时报告。
- patch、日志、截图、测试结果。
- WorkflowStateSnapshot 文件。

规则：

- Agent 默认工作目录是 `RunSandbox`。
- Agent 默认只能写 `RunSandbox`。
- run 完成后扫描新增/修改文件并生成 artifact 索引。
- 成功、失败、取消分别使用不同保留策略。

## 4. 物理路径设计

推荐路径：

```text
runtime-workspace/
  tenants/{tenant_id}/
    workspaces/{workspace_id}/
      projects/{project_id}/
        shared/
        users/{user_id}/
        runs/{run_id}/
```

MVP 可以固定：

```text
tenant_id = default
workspace_id = default
```

因此第一阶段实际路径类似：

```text
runtime-workspace/
  tenants/default/
    workspaces/default/
      projects/{project_id}/
        shared/
        users/{user_id}/
        runs/{run_id}/
```

目录职责：

| 目录 | 含义 | 默认权限 |
|------|------|----------|
| `shared/` | 项目共享区 | run 可读，不可直接写 |
| `users/{user_id}/` | 用户项目作业区 | 当前用户可读写 |
| `runs/{run_id}/` | 单次运行沙盒 | 当前 run 可读写 |

## 5. SandboxContext 和 SandboxBinding

### 5.1 SandboxContext

Bridge 在创建或恢复 run 时构造：

```ts
SandboxContext {
  tenant_id: string
  workspace_id: string
  project_id: string
  user_id: string
  work_item_id?: string
  workflow_instance_id?: string
  workflow_node_instance_id?: string
  runtime_operation_id?: string
  runtime_session_id?: string
  run_id: string
  runtime_type: 'opencode' | 'mock' | 'future'
}
```

### 5.2 SandboxBinding

`SandboxResolver` 输出：

```ts
SandboxBinding {
  tenant_id: string
  workspace_id: string
  project_id: string
  user_id: string
  run_id: string

  project_shared_root: string
  user_project_root: string
  run_root: string
  cwd: string

  readable_roots: string[]
  writable_roots: string[]
  artifact_roots: string[]

  cleanup_policy: 'delete_on_success' | 'retain_on_failure' | 'retain_until_ttl'
  ttl_seconds?: number
}
```

MVP 推荐：

```text
cwd = run_root
readable_roots = [run_root, user_project_root, project_shared_root]
writable_roots = [run_root]
artifact_roots = [run_root]
```

这样 Agent 能读取项目材料和用户作业材料，但默认只能写本次运行沙盒。

## 6. 运行适配要求

所有 Runtime Adapter，包括 OpenCode Adapter，都必须满足：

| 要求 | 说明 |
|------|------|
| Server-side resolve | 前端只传业务 ID，Bridge 解析绝对路径 |
| Path containment | 所有路径必须在 `runtime-workspace` 根下 |
| Symlink guard | 禁止通过符号链接逃逸工作空间 |
| Default write isolation | Agent 默认只能写 `run_root` |
| Explicit promotion | 从 `run_root` 提升到用户或项目空间必须有确认事件 |
| Artifact snapshot | run 结束扫描新增或修改文件，形成 artifact 索引 |
| Idempotent create | 同一个 `run_id` 重试创建不能破坏已有数据 |
| Quota control | 后续支持文件数、大小、时长和进程数限制 |
| Cleanup policy | 成功、失败、取消分别有保留或清理策略 |
| Audit event | 创建、写入、提升、删除都应留下事件 |

## 7. 产物提升流程

```text
RunSandbox file
  -> ArtifactCandidate
    -> User confirmation
      -> UserProjectWorkspace
        -> Project confirmation
          -> ProjectSharedWorkspace
```

MVP 可简化为：

```text
RunSandbox file
  -> Artifact
    -> Confirmation
      -> ProjectSharedWorkspace
```

但即使简化，也要保留：

- 原始 run 路径。
- 目标提升路径。
- 确认人。
- 提升时间。
- 关联 `work_item_id / workflow_node_instance_id / runtime_operation_id`。

## 8. 数据落点

### 8.1 可复用现有字段

| 现有对象 | 可复用字段 |
|----------|------------|
| `work_item` | `project_id`、`space_id`、`project_context_id`、`project_space_id` |
| `workflow_node_instance` | `agent_session_id`、`runtime_session_id`、`agent_state_payload_json` |
| `runtime_operation` | `project_id`、`agent_session_id`、`runtime_session_id`、`work_item_id`、`workflow_instance_id`、`workflow_node_instance_id`、`correlation_id` |
| `artifact` | `work_item_id`、`workflow_node_instance_id`、`session_id`、`storage_uri`、`file_path` |
| `confirmation_request` | `project_id`、`space_id`、`work_item_id`、`workflow_node_instance_id`、`runtime_event_id` |

### 8.2 建议新增字段或表

MVP 可以先不新增大表，只在现有 payload 中记录 `SandboxBinding` 摘要。

第一阶段建议：

- 在 `runtime_operation.command_json` 中记录 `sandboxBinding` 摘要。
- 在 `workflow_node_instance.agent_state_payload_json` 中记录当前 `runRoot`、`invocationId`、`runtimeOperationId`。
- 在 `artifact.file_path` / `storage_uri` 中保存相对 `run_root` 或 `project_shared_root` 的路径。

第二阶段再考虑新增：

```text
runtime_workspace_binding
- id
- tenant_id
- workspace_id
- project_id
- user_id
- run_id
- runtime_operation_id
- project_shared_root
- user_project_root
- run_root
- cleanup_policy
- created_at
```

## 9. 实施计划

### P0：文档和边界确认

目标：

- 固定本设计作为 1026 分支的工作空间实施基线。
- 明确本阶段只做 workspace/sandbox，不做更高层记忆能力。

验收：

- 架构索引已指向本文档。
- 评审结论明确 MVP 路径和不做事项。

### P1：引入 SandboxResolver

改造点：

- 保留 `RuntimeWorkspace.resolve(...)` 作为根目录解析。
- 在 application 层新增 `SandboxResolver` 或扩展 `ProjectRuntimeWorkspaceResolver`。
- 输入 `SandboxContext`，输出 `SandboxBinding`。

验收：

- 同一项目不同 run 得到不同 `run_root`。
- 同一项目不同 user 得到不同 `user_project_root`。
- 所有路径都在 `runtime-workspace` 根下。

### P2：让 skill.run 绑定沙盒

改造点：

- 在工作流节点执行前创建或获取 `SandboxBinding`。
- 将 `cwd = run_root` 传给 OpenCode Runtime Adapter。
- 在 `runtime_operation.command_json` 记录 sandbox 摘要。
- 在 `agent_state_payload_json` 写入当前 run 和 sandbox 摘要。

验收：

- `runtime_operation` 能回答本次运行的工作目录。
- OpenCode 默认在 `run_root` 下执行。
- 用户继续当前节点时仍能恢复同一个 sandbox。

### P3：产物捕获和路径安全

改造点：

- run 结束或节点完成时扫描 `run_root`。
- 新增/修改文件生成 artifact 索引。
- `ArtifactController` 读取文件时必须做 path containment。

验收：

- 只有 `run_root` 或已提升路径内的文件可被展示。
- artifact 能关联 `work_item_id / workflow_node_instance_id / runtime_operation_id`。
- 逃逸路径、符号链接逃逸被拒绝。

### P4：产物提升闭环

改造点：

- 将“提升到用户作业区/项目共享区”建模为 `confirmation_request`。
- 用户确认后由 Bridge 执行文件复制或移动。
- 提升结果写入 artifact 或 notification projection。

验收：

- Agent 不能直接写项目共享区。
- 用户确认后产物可进入目标空间。
- 拒绝确认时项目共享区不发生变化。

### P5：清理、配额和运维

改造点：

- 对 run sandbox 增加 TTL。
- 失败 run 保留，成功 run 可按策略清理。
- 统计每个 project/user/run 的磁盘占用。

验收：

- 过期 run 可清理且不影响已提升产物。
- 清理动作有审计事件。
- 磁盘占用可观测。

## 10. 不做事项

本阶段明确不做：

- 不做完整多租户权限平台。
- 不做跨 workspace 共享。
- 不做长期记忆库和记忆后端适配。
- 不做复杂 RBAC / ABAC。
- 不让前端传绝对路径。
- 不让 Runtime 直接写项目共享区。

## 11. 验收场景

### 11.1 同项目多用户隔离

Alice 和 Bob 都在同一项目下启动 Agent。

期望：

- 两人读取同一个 `project_shared_root`。
- 两人拥有不同的 `user_project_root`。
- 两人的 run 使用不同的 `run_root`。

### 11.2 同用户多次运行隔离

Alice 对同一 work item 连续启动两次 run。

期望：

- 两次 run 的 `run_root` 不同。
- 第二次 run 可以读取用户作业区和项目共享区。
- 第一轮临时文件不会自动污染第二轮。

### 11.3 产物提升

Agent 在 run sandbox 中生成 `hld.md`。

期望：

- `hld.md` 首先只属于当前 run。
- 创建 artifact 索引。
- 用户确认后才提升到项目共享区。

### 11.4 路径逃逸防护

Runtime 输出路径为 `../../outside.txt` 或符号链接指向 workspace 外。

期望：

- Bridge 拒绝读取或提升。
- 记录安全审计事件。

## 12. Decision Record

| 项 | 决策 |
|----|------|
| 是否直接做最终完整版 | 否 |
| 是否固定终局空间骨架 | 是 |
| MVP tenant/workspace | 固定默认值，但路径和 payload 保留字段 |
| Runtime 默认 cwd | `run_root` |
| Runtime 默认写权限 | 只写 `run_root` |
| 项目共享区写入 | 必须通过确认提升 |
| 1026 落点 | `runtime_operation + workflow_node_instance + artifact + confirmation_request` |
| 记忆系统 | 本阶段不纳入 |
