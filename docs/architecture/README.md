# AgentCenter 架构文档入口

> 状态：讨论基线
> 最近更新：2026-05-06
>
> ⚠️ **M1 实施决策**：OpenCode Bridge M1 采用 HTTP+SSE 方案，详见 [ADR-001](./ADR-001-OPENCODE-BRIDGE-SSE-REST.md)。后续实现以 [OPENCODE-BRIDGE-EXECUTION-DESIGN.md](./OPENCODE-BRIDGE-EXECUTION-DESIGN.md) 为准。

本文档用于固定 AgentCenter 后续架构讨论的入口。当前阶段先把文档结构和 4+1 视图边界定下来，后续实现、调研、开源框架参考和产品收敛都围绕这个骨架补充。

## 当前架构基线

AgentCenter 当前首页已经收敛为白色网页端工作台：

- 顶部导航条：项目、空间、迭代三级筛选，全局搜索、通知、设置、用户入口。
- 左侧栏：工作台入口、通用会话、任务会话和设置入口。
- 中心栏：首页任务全景、看板、工作流配置和对话工作台。
- 右侧栏：待确认和事项详情；待确认承接用户确认、审批、补充信息、异常处理和权限确认。
- 底部状态栏：系统状态、工具链连接和智能体在线状态。

这个布局先作为产品和架构的共同基准：左侧组织上下文，中间执行任务，右侧处理待确认和详情，顶部负责全局作用域。

## 推荐阅读顺序

| 顺序 | 文档 | 目的 |
|------|------|------|
| 0 | [DISCUSSION-HANDOFF-2026-04-28.md](./DISCUSSION-HANDOFF-2026-04-28.md) | 跨 Agent 会话交接，快速理解当前讨论进展和下一步 |
| 1 | [../PRODUCT-VISION.md](../PRODUCT-VISION.md) | 理解产品愿景和目标用户价值 |
| 2 | [APPLICATION-ARCHITECTURE-BASELINE.md](./APPLICATION-ARCHITECTURE-BASELINE.md) | 固定企业级应用架构约束：统一上下文、多项目多用户、性能并发、安全治理 |
| 3 | [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) | 理解现有 4+1 架构总览 |
| 4 | [UNIFIED-DOMAIN-MODEL.md](./UNIFIED-DOMAIN-MODEL.md) | 理解核心领域对象和对象关系 |
| 5 | **[OPENCODE-BRIDGE-EXECUTION-DESIGN.md](./OPENCODE-BRIDGE-EXECUTION-DESIGN.md)** | **OpenCode 实施交接设计：HTTP 命令、SSE 输出、数据模型、工作流、待确认和高保真联动** |
| 6 | [AGENT-RUNTIME-BRIDGE-DEVELOPMENT-BLUEPRINT.md](./AGENT-RUNTIME-BRIDGE-DEVELOPMENT-BLUEPRINT.md) | Java Bridge 和 Vue 工作台的总体开发蓝图 |
| 6.5 | [ADR-001-OPENCODE-BRIDGE-SSE-REST.md](./ADR-001-OPENCODE-BRIDGE-SSE-REST.md) | M1 实施决策：HTTP+SSE 方案，Java SSE 基础设施对接 opencode serve |
| 6.6 | [AGENT-RUNTIME-BRIDGE-M1-RUNBOOK.md](./AGENT-RUNTIME-BRIDGE-M1-RUNBOOK.md) | M1 快速启动指南：环境准备、工作目录配置、启动步骤、Troubleshooting |
| 6.7 | [WORKFLOW-CONVERSATION-CLOSURE-DESIGN.md](./WORKFLOW-CONVERSATION-CLOSURE-DESIGN.md) | 首页工作项、后台工作流、任务会话和右侧待确认的闭环实施设计 |
| 6.8 | [RUNTIME-RESOURCE-MANAGEMENT-DESIGN.md](./RUNTIME-RESOURCE-MANAGEMENT-DESIGN.md) | Skill 管理、MCP 管理、运行资源刷新、OpenCode 生效和会话页状态展示 |
| 6.9 | [OPENCODE-BRIDGE-TARGET-STATE.md](./OPENCODE-BRIDGE-TARGET-STATE.md) | 长期目标状态草稿；其中旧 WebSocket 表述已被 M1 HTTP+SSE 设计取代 |
| 7 | [OPENCODE-BRIDGE-CURRENT-STATE.md](./OPENCODE-BRIDGE-CURRENT-STATE.md) | 当前实现做到哪里、Mock 残留在哪里、哪些临时代码不要沿用 |
| 8 | [AGENT-RUNTIME-WEBSOCKET-BRIDGE.md](./AGENT-RUNTIME-WEBSOCKET-BRIDGE.md) | 理解 Vue 工作台、Java Bridge 和 OpenCode Runtime 的 WebSocket 实时对接方案 |
| 9 | [REFERENCE-PROJECTS-AND-RESEARCH.md](./REFERENCE-PROJECTS-AND-RESEARCH.md) | 查看开源框架、竞品调研和可引入经验 |
| 10 | [AI-NATIVE-DEVELOPMENT.md](./AI-NATIVE-DEVELOPMENT.md) | 理解 AI 原生研发流程 |
| 11 | [VERIFICATION-FRAMEWORK.md](./VERIFICATION-FRAMEWORK.md) | 理解验证、测试和可视化闭环 |
| 12 | [ENVIRONMENT-AND-PROMOTION.md](./ENVIRONMENT-AND-PROMOTION.md) | 理解环境隔离和发布晋升 |
| 13 | [INTEGRATION-ROADMAP.md](./INTEGRATION-ROADMAP.md) | 理解存量系统逐步整合路线 |

## 4+1 视图边界

先阅读 [APPLICATION-ARCHITECTURE-BASELINE.md](./APPLICATION-ARCHITECTURE-BASELINE.md) 固定应用架构约束，再展开具体 4+1 视图。当前阶段只定义稳定边界，不提前绑定具体技术选型。

| 视图 | 要回答的问题 | 主要沉淀 |
|------|--------------|----------|
| 逻辑视图 | AgentCenter 有哪些核心领域对象、服务边界和能力模块？ | 领域模型、模块边界、权限模型、工具能力模型 |
| 进程视图 | 用户一句话如何流转成可执行任务？智能体、工具和流程如何协作？ | 编排流程、事件流、状态机、任务生命周期 |
| 开发视图 | 代码如何组织？前端、服务、Agent Runtime、连接器如何分层？ | 模块结构、接口契约、测试策略、依赖边界 |
| 物理视图 | 系统如何部署、扩展、观测和容灾？ | 部署拓扑、运行时、存储、消息、可观测性 |
| 场景视图 | 哪些核心场景验证系统价值？ | 需求转设计、发布风险巡检、故障跟进、代码审查、部署上线 |

## 首页布局与 4+1 的关系

| 首页区域 | 主要对应视图 | 说明 |
|----------|--------------|------|
| 左侧会话和平台能力 | 逻辑视图、开发视图 | 展示上下文、角色、平台能力和工具接入边界 |
| 中心对话工作台 | 场景视图、进程视图 | 承载用户意图、任务编排、执行反馈和人机协作 |
| 中心首页/看板/工作流 | 逻辑视图、进程视图 | 展示事项全景、状态流转和节点配置 |
| 右侧待确认/详情 | 逻辑视图、进程视图 | 展示当前对象，以及需要用户确认、审批、补充和异常处理的事项 |
| 状态栏 | 物理视图 | 展示运行状态、工具连接和智能体在线情况 |

## 外部参考的收敛方式

后续如果引入开源框架代码、竞品材料或专项调研，先进入 [REFERENCE-PROJECTS-AND-RESEARCH.md](./REFERENCE-PROJECTS-AND-RESEARCH.md) 做登记。只有当某个参考会影响架构边界、依赖选型或产品形态时，再进一步沉淀为 ADR 或更新 4+1 视图。

建议节奏：

1. 登记来源：项目名、仓库、文档、截图、代码路径。
2. 标注分类：Agent 编排、工作流、DevOps、知识库、连接器、可观测性、权限治理等。
3. 提炼经验：哪些交互、架构、模块或机制值得借鉴。
4. 判断引入方式：概念借鉴、模式复用、适配器集成、直接依赖、拒绝引入。
5. 回写影响：如果影响产品或架构，更新对应 4+1 视图和 ADR。
