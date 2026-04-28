# AgentCenter 讨论进展交接

> 日期：2026-04-28
> 用途：给后续 Agent 会话快速接续当前产品、原型、架构和参考项目讨论。
> 当前状态：讨论基线已初步形成，尚未进入具体技术选型。

## 给下一个 Agent 的第一句话

本项目当前不要继续发散成“收集更多框架”或“马上选型实现”。请先围绕已经形成的应用架构基线，继续收敛 AgentCenter 的 4+1 视图、核心领域对象和企业级约束。

最重要的共识：

> AgentCenter 不是“聊天框 + RAG”，而是企业内部的 **AI 工作台 + 上下文平台 + Agent 运行控制面**。

## 用户的核心诉求

用户希望 AgentCenter 最终成为企业内部多项目、多用户的 AI 工作台，用来把过去多个“断点项目”里的信息统一起来，并让 Agent 能基于可信上下文执行研发、运维、治理类任务。

已经明确的重点：

- 要有统一上下文，不只是普通知识库。
- 要支持企业内部多项目、多团队、多用户、多 Agent。
- 要把首页工作台布局先定下来，作为后续产品和架构讨论的共同入口。
- 要先设计 4+1 视图和大的应用架构，后续再讨论具体实现和技术选型。
- 要把参考项目和调研经验沉淀成 AgentCenter 自己的独立文档，不能依赖其它本地项目。
- 要考虑性能并发、安全、权限、审计、企业 IAM、内部平台集成。

## 当前首页高保真基线

当前首页已收敛为白色网页端、VS Code 式左中右三栏工作台。

活跃文件：

- [静态原型](../prototype/homepage.html)
- [React Demo 首页](../../agent-center-demo/client/index.html)
- [原型归档入口](../prototype/README.md)
- [当前首页工作台基线归档](../prototype/archive/homepage-workbench-2026-04-27/README.md)

当前布局基线：

- 顶部导航条：可展开/收起。
- 顶部全流程阶段面板：默认收起，展开展示阶段详情和节点状态。
- 左侧栏：默认展开会话列表，包含平台导航、通用工具链、智能体状态。
- 中心栏：对话工作台，是首页主焦点。
- 右侧栏：上下文详情、主动预警、智能体协作。
- 底部历史面板：默认收起，占用面积小，展开展示最近活动、全流程历史和执行记录。
- 状态栏：展示运行状态、工具连接和在线状态。

后续可以调整内容、指标、示例数据、文案和业务卡片，但尽量保持上述结构作为初始布局框架。

## 当前应用架构基线

必须优先阅读：

- [APPLICATION-ARCHITECTURE-BASELINE.md](./APPLICATION-ARCHITECTURE-BASELINE.md)
- [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md)
- [UNIFIED-DOMAIN-MODEL.md](./UNIFIED-DOMAIN-MODEL.md)

当前已固定四个企业级架构约束：

| 约束 | 当前理解 |
|------|----------|
| 统一上下文 | 建议称为 `Context & Knowledge Platform`，包含证据、关系、索引、记忆、上下文构建和权限过滤，不只是向量库 |
| 多项目多用户 | 以 `Tenant / Workspace / Project / User / Team / Role / Agent / Run` 建模，Project 是默认隔离单元 |
| 性能并发 | 前台对话、后台任务、索引同步、投影查询分链路设计，并按 user/project/connector/run/tenant 控制配额 |
| 企业安全 | 对接企业 IAM，尊重源系统权限，所有 Agent 行为经过 Policy、Approval、Audit、Sandbox、Secret Vault |

应用架构骨架：

```text
Web Workbench
  -> API / Gateway / BFF
    -> Identity & Access Boundary
    -> Context & Knowledge Platform
    -> Conversation & Session Service
    -> Agent Runtime
    -> Run / Plan / Step Engine
    -> Workflow / Automation Engine
    -> Tool & Connector Hub
    -> Memory / Skill System
    -> Approval / Policy / Audit
    -> Event Store / Projection / Observability
```

关键原则：

- 源系统仍是 source of truth，AgentCenter 保存证据、索引、摘要、引用和关系。
- 检索必须尊重权限，不能让 Agent 绕过企业已有权限体系。
- `Session` 服务交互体验，`Run / Event` 才是运行事实层。
- 聊天消息、流程面板、右侧卡片、底部历史都应该从事实层投影出来。
- 高风险动作必须进入策略判断、人工审批和审计链路。

## 4+1 视图当前方向

| 视图 | 下一步应收敛的内容 |
|------|--------------------|
| 场景视图 | 多项目用户从首页发起任务，系统如何补齐上下文、计划、执行、审批、复盘 |
| 逻辑视图 | Tenant、Workspace、Project、User、Agent、Run、Task、Tool、Skill、Memory、Approval、Event 的关系 |
| 进程视图 | 用户意图如何经过 Context Builder、Planner、Agent Runtime、Tool Hub、Approval、Event Store |
| 开发视图 | Web Workbench、Gateway/BFF、Context Platform、Runtime、Connector、Governance 的模块边界 |
| 物理视图 | Web/API、workers、sandbox、stores、indexes、observability 如何部署和扩展 |

现有 [ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md) 中部分技术栈、消息队列、存储、中间件和部署形态仍属于早期草案。除非后续 ADR 明确确认，不应把它们当作当前选型结论。

## 参考项目范围

主要登记文档：

- [REFERENCE-PROJECTS-AND-RESEARCH.md](./REFERENCE-PROJECTS-AND-RESEARCH.md)
- [Agentic Workbench 参考实践](../research/agentic-workbench-reference-patterns/README.md)

当前 P0 / 重点参考：

| 方向 | 项目 |
|------|------|
| 工作台交互和任务主路径 | Qoder Quest / Experts、OpenCode、OpenClaw |
| Agent-to-UI | A2UI，后续可补 AG-UI / CopilotKit |
| 单 Agent 内核 | OpenCode、claude-code-rust |
| 多 Agent 编排 | OpenClaw、Open Multi-Agent、ClawTeam、crewAI、deer-flow、LangGraph |
| 长流程可靠性 | Temporal、Lobster |
| 记忆和技能演化 | Hermes Agent、mem0、MAGMA、OpenSpace、OpenHarness |
| 控制面和多端 | OpenClaw、Skales、OpenCode Desktop |

已经讨论过的重点补充：

- `deer-flow` 已纳入范围，但在总登记表里和 LangGraph 合并出现，后续建议拆成独立条目。
- `OpenClaw` 已作为 P0 参考，学习 Gateway 控制面、session/task ledger、skills/plugin、sandbox/approval、多端 surface。
- `Hermes Agent` 已作为自学习 Agent / 自动化网关参考，学习技能沉淀、跨会话记忆、cron/webhook、多渠道交付、远程执行、评测训练。
- 参考项目只作为模式复用和概念借鉴，不直接污染 AgentCenter 内部领域模型。

## 重要边界和不要做的事

下一个 Agent 不要做这些事：

- 不要把 AgentCenter 设计成单纯聊天框或普通 RAG 产品。
- 不要马上替用户做技术选型，例如直接定 Temporal、LangGraph、deer-flow、某个向量库。
- 不要把其它本地项目路径或历史项目名写成 AgentCenter 的依赖。
- 不要把外部项目名词直接变成核心数据库字段或 UI 主文案。
- 不要假设 Agent 可以持有万能密钥或绕过源系统权限。
- 不要把旧的 `a-version*.html` 当作未来首页基线。

## 建议下一轮讨论顺序

建议从这些问题继续：

1. 定义 `Context & Knowledge Platform` 的领域模型：Evidence、SourceRef、KnowledgeCollection、ContextPackage、ACL、Index。
2. 定义多租户/多项目边界：Tenant、Workspace、Project、Team、Role、Agent 的关系。
3. 定义运行事实层：Run、Plan、Step、ToolCall、Approval、Artifact、Event 的状态机。
4. 定义权限链路：企业 IAM -> 项目权限 -> Connector 权限 -> Tool Policy -> Approval -> Audit。
5. 定义性能并发模型：前台流式、后台任务、索引同步、投影查询和配额。
6. 再进入 4+1 各视图，把上述对象投影到场景、逻辑、进程、开发、物理视图。

## 当前文件状态

截至本交接文档创建时，上一轮讨论产生的文档和原型归档仍处于未提交状态。请先查看 `git status --short`，不要覆盖用户或其它 Agent 的未提交改动。

关键新增或更新文件包括：

- [docs/architecture/README.md](./README.md)
- [docs/architecture/APPLICATION-ARCHITECTURE-BASELINE.md](./APPLICATION-ARCHITECTURE-BASELINE.md)
- [docs/architecture/REFERENCE-PROJECTS-AND-RESEARCH.md](./REFERENCE-PROJECTS-AND-RESEARCH.md)
- [docs/architecture/ARCHITECTURE-OVERVIEW.md](./ARCHITECTURE-OVERVIEW.md)
- [docs/architecture/UNIFIED-DOMAIN-MODEL.md](./UNIFIED-DOMAIN-MODEL.md)
- [docs/research/agentic-workbench-reference-patterns/README.md](../research/agentic-workbench-reference-patterns/README.md)
- [docs/prototype/README.md](../prototype/README.md)
- [docs/prototype/archive/homepage-workbench-2026-04-27/README.md](../prototype/archive/homepage-workbench-2026-04-27/README.md)
- [docs/README.md](../README.md)

## 给后续 Agent 的操作建议

开始新会话后，建议先读：

1. 本文档。
2. [APPLICATION-ARCHITECTURE-BASELINE.md](./APPLICATION-ARCHITECTURE-BASELINE.md)。
3. [docs/prototype/README.md](../prototype/README.md)。
4. [REFERENCE-PROJECTS-AND-RESEARCH.md](./REFERENCE-PROJECTS-AND-RESEARCH.md)。

然后再根据用户当下问题进入具体视图或模块，不要从参考项目调研重新开始。

