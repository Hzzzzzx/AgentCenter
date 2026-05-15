# AgentCenter 当前功能 LLD

> 状态：当前功能 LLD 基线
> 最近更新：2026-05-15
> 功能盘点基线：`6f2e04164541ae51c9e11803dcdfb0fd7d25858d`（2026-05-14 10:26:04 +0800，`docs(project-context): slim enterprise provider guidance`）
> 说明：本文按上述 10:26 commit 的工程状态定义当前功能 LLD；之后 commit 中新增的内容视为后续演进或补充文档。
> 关联文档：[当前功能 PRD](./CURRENT-FEATURE-PRD.md) | [当前功能 HLD](./CURRENT-FEATURE-HLD.md)

本文档按实现包列出主要文件、接口、表和测试点，供团队拆任务和代码走查使用。

## 一、前端实现地图

### 1. 工作台壳层

| 功能 | 文件 |
|------|------|
| 应用状态和页面路由 | `agentcenter-web/src/App.vue` |
| 三栏布局 | `agentcenter-web/src/components/shell/AppShell.vue` |
| 顶部项目上下文 | `agentcenter-web/src/components/shell/TitleBar.vue` |
| 左侧导航和会话列表 | `agentcenter-web/src/components/shell/LeftSidebar.vue` |
| 右侧待确认/详情/产物 | `agentcenter-web/src/components/shell/RightPanel.vue` |
| 状态栏 | `agentcenter-web/src/components/shell/StatusBar.vue` |
| 主题 | `agentcenter-web/src/stores/theme.ts`, `agentcenter-web/src/theme/themes.ts`, `agentcenter-web/src/styles/themes.css` |

测试点：

- 壳层折叠/展开不影响中间工作区。
- 选择工作项时右侧切到详情。
- 打开产物时右侧切到产物预览。

### 2. 首页与看板

| 功能 | 文件 |
|------|------|
| 首页统计和工作项列表 | `agentcenter-web/src/views/HomeOverview.vue` |
| 看板列 | `agentcenter-web/src/views/BoardView.vue` |
| 工作流投影 | `agentcenter-web/src/stores/workItemWorkflowProjection.ts` |
| 工作项 store | `agentcenter-web/src/stores/workItems.ts` |
| 工作项 API | `agentcenter-web/src/api/workItems.ts` |

测试点：

- 类型/状态筛选正确。
- 批量启动只对当前类型和初始状态工作项生效。
- workflow summary 能投影为当前节点和状态。

### 3. 工作流配置

| 功能 | 文件 |
|------|------|
| 编排配置页 | `agentcenter-web/src/views/WorkflowConfig.vue` |
| 工作流 store | `agentcenter-web/src/stores/workflows.ts` |
| Workflow API | `agentcenter-web/src/api/workflows.ts` |
| Skill API | `agentcenter-web/src/api/runtimeResources.ts` |

测试点：

- 只允许选择当前项目可用 Skill。
- 保存新版后重新加载 definitions。
- Mermaid 路线和阶段草案同步更新。

### 4. 对话工作台

| 功能 | 文件 |
|------|------|
| 会话页 | `agentcenter-web/src/views/ConversationWorkbench.vue` |
| 消息列表 | `agentcenter-web/src/components/conversation/MessageList.vue` |
| Assistant turn 投影 | `agentcenter-web/src/components/conversation/AssistantTurn.vue` |
| 运行过程 | `ExecutionSteps.vue`, `ExecutionStepItem.vue`, `ProcessTrace.vue` |
| 节点控制 | `WorkflowNodeControlBar.vue` |
| 交互栏 | `ConversationInteractionBar.vue` |
| Runtime SSE store | `agentcenter-web/src/stores/runtime.ts` |
| Session store | `agentcenter-web/src/stores/sessions.ts` |

测试点：

- SSE delta 不重复拼接。
- ASSISTANT_COMPLETED 后能回拉最终消息。
- 历史版本只读。
- 运行中输入和交互处理有明确阻断提示。

### 5. 确认与交互

| 功能 | 文件 |
|------|------|
| 待确认面板 | `agentcenter-web/src/components/confirmation/ConfirmationPanel.vue` |
| 确认卡片和弹窗 | `agentcenter-web/src/components/confirmation/ConfirmationCard.vue` |
| 交互 schema | `agentcenter-web/src/components/conversation/interactions/interactionSchema.ts` |
| 交互提交转换 | `agentcenter-web/src/components/conversation/interactions/interactionSubmit.ts` |
| 表单渲染 | `InteractionResponseForm.vue` |
| confirmation store | `agentcenter-web/src/stores/confirmations.ts` |

测试点：

- DECISION / INPUT / APPROVAL / PERMISSION / EXCEPTION 渲染正确。
- 提交 payload 与后端 ResolveConfirmationRequest 对齐。
- enter-session 进入 confirmation 绑定会话。

### 6. Skill / MCP / Runtime 设置

| 功能 | 文件 |
|------|------|
| Skill 管理 | `agentcenter-web/src/views/SkillManagement.vue` |
| MCP 管理 | `agentcenter-web/src/views/McpManagement.vue` |
| Runtime 设置 | `agentcenter-web/src/views/RuntimeSettings.vue` |
| 运行资源轻量页 | `agentcenter-web/src/views/RuntimeResources.vue` |
| 项目管理 | `agentcenter-web/src/views/ProjectContextSettings.vue` |
| runtime settings store | `agentcenter-web/src/stores/runtimeSettings.ts` |

测试点：

- 上传/更新 Skill ZIP 后刷新列表。
- MCP 启停/测试/刷新工具有错误反馈。
- 切换 provider 后重新 sync 并刷新 work items。

## 二、Bridge 实现地图

### 1. API Controller

| Controller | 职责 |
|------------|------|
| `WorkItemController` | 工作项 list / overview / get / create / update |
| `WorkItemWorkflowController` | 单个/批量启动、重启、版本列表 |
| `WorkflowController` | workflow definition、instance、continue、retry、skip |
| `AgentSessionController` | 会话、消息、runtime resources、cancel |
| `RuntimeEventStreamController` | 会话 SSE |
| `ConfirmationController` | 待确认查询、进入会话、resolve/reject |
| `ArtifactController` | 产物查询和工作项产物列表 |
| `ProjectDataProviderController` | provider 设置、同步、snapshot、history |
| `ProjectRuntimeResourceController` | Skill/MCP 管理 |
| `RuntimeResourceController` | runtime status 和轻量 Skill refresh |
| `HealthController` | 健康检查 |

### 2. Application Service

| Service | 职责 |
|---------|------|
| `WorkItemService` | 工作项查询、概览聚合、创建更新 |
| `WorkflowCommandService` | 启动、继续、重启、节点执行、产物、确认、状态流转 |
| `AgentSessionService` | 会话、消息、普通对话、工作流消息路由、取消 |
| `RuntimeEventService` | 运行事件落库和广播 |
| `ConfirmationService` | 待确认处理和回灌 |
| `SkillRegistryService` | Skill 上传、版本、启停、删除、校验 |
| `McpRegistryService` | MCP 导入、启停、测试、工具快照 |
| `RuntimeResourceService` | Runtime Skill 扫描和刷新 |
| `ProjectDataSyncService` | Provider 同步、upsert、历史 |
| `ProjectDataProviderSettingsService` | active provider/scope |
| `ArtifactCaptureService` | 从消息/事件捕获产物 |
| `WorkflowContextAnchorService` | 工作流上下文锚点 |

### 3. Runtime 层

| 文件 / 包 | 职责 |
|-----------|------|
| `application/runtime/RuntimeGateway.java` | 应用层统一 Runtime 入口 |
| `DefaultRuntimeGateway.java` | Provider 路由和 operation tracking |
| `RuntimeProvider.java` | Runtime Provider 接口 |
| `DefaultRuntimeProviderRegistry.java` | RuntimeType -> Provider |
| `application/runtime/protocol/*` | Command/Event/Ack 协议信封 |
| `application/runtime/transport/*` | 命令和事件传输抽象 |
| `infrastructure/runtime/opencode/*` | OpenCode Provider、Adapter、Process、SSE、Skill/MCP 文件 |
| `infrastructure/runtime/aruntime/*` | A Runtime 骨架 |
| `application/runtime/translation/*` | 统一事件到消息/确认/operation 的投影 |

测试点：

- Runtime session id 和 AgentCenter session id 不混用。
- OpenCode prompt_async payload 契约正确。
- SSE event 能转为 ASSISTANT_DELTA、STATUS、PERMISSION_REQUIRED、CONFIRMATION_CREATED。
- Runtime 操作失败能写 RuntimeOperation failed。

## 三、数据库 / Migration 地图

| 表 | 主要用途 | Migration |
|----|----------|-----------|
| `work_item` | 统一事项 | `V1`, `V21` |
| `workflow_definition` | 工作流定义 | `V1`, `V5`, `V9`, `V22` |
| `workflow_node_definition` | 节点定义 | `V1`, `V10` |
| `workflow_instance` | 工作流实例 | `V1`, `V17` |
| `workflow_node_instance` | 节点实例 | `V1`, `V10`, `V16` |
| `agent_session` | 平台会话 | `V1`, `V5` |
| `agent_message` | 消息 | `V1`, `V14` |
| `runtime_event` | 运行事件 | `V1`, `V15` |
| `confirmation_request` | 待确认 / 交互 | `V1`, `V16` |
| `confirmation_action` | 确认处理记录 | `V1` |
| `artifact` | 产物 | `V1`, `V20` |
| `runtime_skill` | Skill 登记 | `V6` |
| `runtime_skill_version` | Skill 版本 | `V6` |
| `project_mcp_server` | MCP Server | `V6` |
| `project_mcp_tool_snapshot` | MCP tool 快照 | `V6` |
| `runtime_resource_audit` | 资源操作审计 | `V6` |
| `runtime_operation` | Runtime 操作跟踪 | `V13` |
| `project_context` / `project_space` / `project_iteration` | 项目上下文 | `V21` |
| `project_provider_setting` | 当前 provider/scope | `V21`, `V23` |
| `project_data_sync_history` | 同步历史 | `V21` |

## 四、关键接口清单

### Work Items

```text
GET  /api/work-items
GET  /api/work-items/overview
GET  /api/work-items/{id}
POST /api/work-items
PUT  /api/work-items/{id}
```

### Workflow

```text
GET  /api/workflow-definitions?projectId=
PUT  /api/workflow-definitions/{id}
GET  /api/workflow-instances/{id}
POST /api/workflow-instances/{id}/continue
POST /api/workflow-node-instances/{id}/retry
POST /api/workflow-node-instances/{id}/skip
POST /api/work-items/{id}/start-workflow
POST /api/work-items/{id}/restart-workflow
GET  /api/work-items/{id}/workflow-versions
POST /api/work-items/start-workflows
```

### Sessions and Events

```text
GET  /api/agent-sessions
POST /api/agent-sessions
GET  /api/agent-sessions/{id}
GET  /api/agent-sessions/{id}/messages
POST /api/agent-sessions/{id}/messages
POST /api/agent-sessions/{id}/cancel
GET  /api/agent-sessions/{id}/events
GET  /api/agent-sessions/{id}/runtime-resources
```

### Confirmations and Artifacts

```text
GET  /api/confirmations
GET  /api/confirmations/{id}
POST /api/confirmations/{id}/enter-session
POST /api/confirmations/{id}/resolve
POST /api/confirmations/{id}/reject
GET  /api/artifacts/{id}
GET  /api/work-items/{workItemId}/artifacts
```

### Project Data and Runtime Resources

```text
GET  /api/project-data-providers
PUT  /api/project-data-providers/active
PUT  /api/project-data-providers/active-scope
GET  /api/project-data-providers/snapshot
POST /api/project-data-providers/sync
POST /api/project-data-providers/select-and-sync
GET  /api/project-data-providers/sync-history

GET    /api/projects/{projectId}/runtime/skills
POST   /api/projects/{projectId}/runtime/skills/upload
PUT    /api/projects/{projectId}/runtime/skills/{skillId}/zip
POST   /api/projects/{projectId}/runtime/skills/{skillId}/enable
POST   /api/projects/{projectId}/runtime/skills/{skillId}/disable
DELETE /api/projects/{projectId}/runtime/skills/{skillId}
POST   /api/projects/{projectId}/runtime/skills/refresh
GET    /api/projects/{projectId}/runtime/mcps
POST   /api/projects/{projectId}/runtime/mcps/import
POST   /api/projects/{projectId}/runtime/mcps/{mcpId}/enable
POST   /api/projects/{projectId}/runtime/mcps/{mcpId}/disable
POST   /api/projects/{projectId}/runtime/mcps/{mcpId}/test
POST   /api/projects/{projectId}/runtime/mcps/{mcpId}/refresh-tools
```

## 五、实现拆分建议

| 任务 | 文件范围 | 验证 |
|------|----------|------|
| 项目同步 | `projectcontext/*`, `ProjectDataProviderController`, `ProjectContextSettings.vue` | `ProjectDataProviderControllerTest`, 前端项目切换测试 |
| 首页/看板 | `WorkItemService`, `workItems.ts`, `HomeOverview.vue`, `BoardView.vue` | `WorkItemControllerTest`, `HomeOverview.test.ts`, `BoardView.test.ts` |
| 工作流执行 | `WorkflowCommandService`, `WorkflowPromptComposer`, `WorkflowNodeStateParser` | `M1WorkflowStartIntegrationTest`, `WorkflowConversationInteractionTest` |
| 对话实时流 | `AgentSessionService`, `RuntimeEventStreamController`, `runtime.ts`, `ConversationWorkbench.vue` | `AgentSessionControllerTest`, `RuntimeEventStreamControllerTest`, `runtime.test.ts` |
| 待确认 | `ConfirmationService`, `InteractionMapper`, `ConfirmationPanel/Card`, `ConversationInteractionBar` | `ConfirmationServiceTest`, `ConfirmationCard.test.ts`, `ConversationInteractionBar.test.ts` |
| Runtime Provider | `RuntimeGateway`, `opencode/*`, `runtime/protocol/*` | `OpenCodeRuntime*Test`, `RuntimeEnvelopeSerializationTest` |
| Skill/MCP | `SkillRegistryService`, `McpRegistryService`, `SkillManagement.vue`, `McpManagement.vue` | `SkillRegistryServiceTest`, `RuntimeResourceServiceTest` |
| 产物 | `ArtifactCaptureService`, `ArtifactController`, `ArtifactViewer.vue` | `ArtifactCaptureServiceTest`, `ArtifactControllerTest`, `ArtifactViewer.test.ts` |

## 六、验证命令

按修改范围选择：

```bash
# Vue 工作台
cd agentcenter-web
npm run typecheck
npm run test
npm run build

# Java Bridge
cd agentcenter-bridge
./mvnw test
./mvnw clean package
```

UI 或原型改动必须留下 Playwright 截图证据到 `.sisyphus/evidence/`。
