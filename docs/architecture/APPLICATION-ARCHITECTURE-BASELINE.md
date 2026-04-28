# AgentCenter 应用架构基线

> 状态：讨论基线
> 最近更新：2026-04-27
> 范围：先固定企业级应用架构、核心组件和 4+1 视图关注点；暂不决定具体框架、数据库、消息队列、向量库或云基础设施。

## 设计结论

AgentCenter 不是“聊天框 + RAG”，而是企业内部的 **AI 工作台 + 上下文平台 + Agent 运行控制面**。

当前阶段先固定四个架构约束：

| 约束 | 含义 |
|------|------|
| 统一上下文 | 把断点项目中的需求、代码、文档、接口、部署、故障、讨论、审批和历史决策统一成可检索、可追溯、带权限的上下文资产 |
| 多项目多用户 | 支持企业内部多个项目、团队、用户、Agent 和工作区，所有数据和操作都有明确作用域 |
| 性能并发 | 前台对话、后台任务、索引同步、工具调用分离，通过队列、事件、投影、缓存、配额控制吞吐和延迟 |
| 企业安全 | 对接企业 IAM 和内部平台权限，所有 Agent 行为经过策略、审批、审计、沙箱和密钥治理 |

## 非目标

本阶段不做这些决定：

- 不决定用哪一个 Agent 框架。
- 不决定工作流引擎、消息队列、数据库、向量库、对象存储。
- 不把任何参考项目的概念直接变成 AgentCenter 的核心字段。
- 不假设 Agent 可以绕过企业已有权限体系。
- 不把聊天消息当成唯一事实源。

## 应用架构骨架

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

核心原则：

- Web Workbench 是主入口，承载对话、上下文、流程、协作和历史。
- Gateway / BFF 负责会话、流式输出、权限上下文和前端投影，不承载复杂业务状态。
- Context & Knowledge Platform 是企业上下文底座，不等同于单一向量库。
- Agent Runtime 只消费经过授权和裁剪的上下文，不直接读取全部企业数据。
- Run / Step / Event 是运行事实层，聊天消息和面板只是投影。
- Tool & Connector Hub 通过适配器连接 Git、Issue、CI、监控、工单、知识库、制品库等内部平台。
- Policy / Approval / Audit 是横切能力，不附属于某一个工具。

## 统一上下文平台

统一上下文建议命名为 `Context & Knowledge Platform`，它包含证据、关系、索引、记忆和上下文构建，不只是“知识库”。

```text
Source Systems
  -> Connectors
  -> Raw Evidence Store
  -> Canonical Object Graph
  -> Search Index / Vector Index
  -> Context Builder
  -> Agent Runtime / Workbench Projections
```

### 分层职责

| 层 | 职责 | 示例 |
|----|------|------|
| Source Systems | 企业现有事实源 | Git、Jira、Confluence、CI、监控、CMDB、工单、制品库 |
| Connectors | 增量同步、权限同步、事件监听 | webhook、polling、API sync、导入任务 |
| Raw Evidence Store | 保存原始证据和快照 | 文档版本、提交 diff、工单正文、日志片段、审批记录 |
| Canonical Object Graph | 把不同系统对象映射到统一关系图 | Project、Service、Repo、WorkItem、Deployment、Incident |
| Retrieval Indexes | 检索和召回 | 关键词索引、向量索引、图关系索引、时间线索引 |
| Context Builder | 按用户、项目、任务和权限构建上下文包 | 当前任务上下文、相关代码、历史事故、风险提示 |
| Projections | 给工作台展示的视图 | 上下文详情、风险卡片、执行历史、证据链 |

### 上下文原则

- 源系统仍是 source of truth，AgentCenter 保存证据、索引、摘要、引用和关系。
- 每个上下文片段都必须带来源、版本、时间、所属项目和权限信息。
- 检索阶段必须做权限过滤，不能先召回再在回答里“假装不展示”。
- 用户记忆、项目知识、组织知识、技能经验、执行追踪要分开建模。
- Agent 的回答和动作必须能追溯到证据链。

## 多项目多用户模型

建议先按下面的作用域设计：

```text
Tenant / Organization
  -> Workspace
    -> Project
      -> Service / Repository / Environment / Workflow
      -> Knowledge Collection
      -> Agent
      -> Session / Run / Task
  -> User / Team / Role / Policy
```

核心判断：

- `Tenant / Organization` 是企业边界。
- `Workspace` 是协作边界，可以承载一组项目、一类团队或一个业务域。
- `Project` 是主要隔离单元，默认所有知识、任务、Agent、工具权限都落在项目作用域内。
- `User / Team / Role` 负责身份和授权，不应散落在 Agent 或工具配置里。
- `Agent` 可以是平台级、工作区级或项目级，但执行时必须绑定一个明确的用户和项目上下文。

## 运行事实层

AgentCenter 后续应把运行事实和 UI 展示分开。

```text
Run
  -> Plan
    -> Step
      -> ToolCall
      -> Approval
      -> Artifact
      -> Event
```

| 对象 | 含义 |
|------|------|
| Run | 一次用户意图或自动化触发形成的完整运行 |
| Plan | 对 Run 的任务拆解和执行策略 |
| Step | 可执行或可审查的最小阶段 |
| ToolCall | 调用内部平台、代码执行、检索、部署等工具的事实记录 |
| Approval | 人工确认、策略豁免、风险接受 |
| Artifact | 代码补丁、报告、配置、计划、摘要、截图、日志包等产物 |
| Event | append-only 事实事件，用于审计、回放和投影 |

聊天消息、流程面板、右侧协作卡片、底部历史列表都应该从这些事实投影出来。

## 性能与并发设计

先区分四条链路：

| 链路 | 目标 | 架构要求 |
|------|------|----------|
| 前台对话链路 | 低延迟、可流式、可取消 | 同步入口轻量化，长耗时动作转后台 |
| 后台任务链路 | 可恢复、可重试、可审计 | 队列、worker、超时、幂等、状态机 |
| 索引同步链路 | 不阻塞用户操作 | 增量同步、变更检测、失败重放 |
| 投影查询链路 | 快速展示工作台 | CQRS/读模型、缓存、分页、订阅更新 |

并发控制需要至少覆盖：

- `per user`：限制个人同时运行的任务和模型消耗。
- `per project`：避免单个项目占满全局资源。
- `per connector`：保护 Git、Issue、CI、监控等内部系统。
- `per run`：限制一次运行的子任务、工具调用、token、时长和产物大小。
- `per tenant`：保障企业级隔离和成本控制。

后台任务必须支持取消、超时、重试、幂等、补偿、降级和状态可见。

## 企业安全与治理

```text
Enterprise IAM / SSO
  -> User / Group / Role
  -> Project Policy
  -> Connector Authorization
  -> Agent Tool Policy
  -> Approval / Audit / Sandbox
```

安全基线：

1. 登录对接企业 IAM，OIDC、SAML、LDAP、企业微信、钉钉等都只是适配器。
2. 内部统一成 `User / Group / Team / Role / Policy`。
3. 检索必须尊重源系统权限：优先 query-as-user；不能时，索引同步 ACL 并在查询时过滤。
4. Agent 不持有万能密钥，所有凭证进入 Secret Vault，工具调用使用最小权限临时凭证。
5. 高风险动作必须经过 policy 和 approval，例如发版、删数据、改配置、提交代码、操作生产环境。
6. 所有行为必须审计：谁发起、Agent 看了什么、调用什么、改了什么、谁审批、结果如何。
7. 工具执行默认隔离：脚本、浏览器、文件读写、代码执行进入 sandbox。
8. 权限、审计和数据隔离是产品能力，不是部署后补项。

## 4+1 视图落点

| 视图 | 本阶段先固定的问题 |
|------|--------------------|
| 场景视图 | 多项目用户如何从首页发起任务，系统如何补齐上下文、计划、执行、审批、复盘 |
| 逻辑视图 | Tenant、Workspace、Project、User、Agent、Run、Task、Tool、Skill、Memory、Approval、Event 的关系 |
| 进程视图 | 用户意图如何经过 Context Builder、Planner、Agent Runtime、Tool Hub、Approval、Event Store |
| 开发视图 | Web Workbench、Gateway/BFF、Context Platform、Runtime、Connector、Governance 的模块边界 |
| 物理视图 | Web/API、workers、sandbox、stores、indexes、observability 如何部署和扩展 |

## 后续选型再讨论

后续技术选型可以按能力域逐个讨论：

| 能力域 | 可参考项目 | 选型时关注 |
|--------|------------|------------|
| Agent 编排 | OpenClaw、deer-flow、LangGraph、Open Multi-Agent、ClawTeam | 状态模型、子任务、并发、可恢复、工具边界 |
| 长流程执行 | Temporal、Lobster | checkpoint、retry、approval、determinism、运维复杂度 |
| 上下文和记忆 | mem0、Letta、MAGMA、OpenSpace、OpenHarness | ACL、来源追溯、记忆分层、技能演化 |
| Agent-to-UI | A2UI、AG-UI、CopilotKit | 安全渲染、声明式 UI、action contract、多端兼容 |
| 企业门户 | Backstage、OpenClaw Control UI | 服务目录、插件化、权限、可观测入口 |

