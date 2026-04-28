# 参考项目与调研登记表

> 状态：持续补充
> 最近更新：2026-04-27
> 用途：记录后续评估的开源框架、竞品平台、源码参考、调研结论和可引入经验。

## 目标

AgentCenter 后续会持续参考开源框架、企业 AI 平台、DevOps AI 产品和自动化编排系统。本文档不是简单收藏链接，而是用于回答三个问题：

1. 这个项目或调研材料对 AgentCenter 有什么启发？
2. 它影响 4+1 视图中的哪一部分？
3. 我们应该如何引入：概念借鉴、模式复用、适配器集成、直接依赖，还是明确不引入？

## 登记模板

| 字段 | 说明 |
|------|------|
| 项目/材料 | 开源项目、产品调研、论文、源码包或用户提供材料名称 |
| 来源 | GitHub 仓库、文档路径、本地代码路径或调研报告路径 |
| 分类 | Agent 编排、工作流引擎、DevOps、知识库、连接器、可观测性、权限治理等 |
| 可借鉴点 | 具体值得学习的产品能力、架构模式、代码组织或交互方式 |
| 关联视图 | 逻辑视图、进程视图、开发视图、物理视图、场景视图 |
| 引入方式 | 概念借鉴、模式复用、适配器集成、直接依赖、暂不引入、拒绝引入 |
| 状态 | 待阅读、已初评、可原型验证、可引入、已拒绝 |
| 备注 | 风险、许可证、复杂度、替代方案、后续动作 |

## 当前参考登记

| 项目/材料 | 来源 | 分类 | 可借鉴点 | 关联视图 | 引入方式 | 状态 |
|-----------|------|------|----------|----------|----------|------|
| 企业级 AI Agent 平台综合调研 | [../research/enterprise-ai-summary/README.md](../research/enterprise-ai-summary/README.md) | 企业 AI 平台 | 企业级 Agent 平台通常需要上下文、工具、权限、审计和治理闭环 | 逻辑视图、物理视图 | 模式复用 | 已初评 |
| DevOps AI Agent 综合调研 | [../research/devops-ai-platforms/devops-ai-agent-research-2026-03-26.md](../research/devops-ai-platforms/devops-ai-agent-research-2026-03-26.md) | DevOps AI | AI 能力必须贴住需求、代码、构建、测试、部署、监控链路，而不是只做聊天入口 | 进程视图、场景视图 | 模式复用 | 已初评 |
| Microsoft/GitHub DevOps AI | [../research/devops-ai-platforms/microsoft-github-devops-ai/README.md](../research/devops-ai-platforms/microsoft-github-devops-ai/README.md) | DevOps / 开发者工具 | 代码、PR、CI、Issue 上下文是研发智能体的重要输入 | 逻辑视图、场景视图 | 概念借鉴 | 待回读 |
| GitLab Duo AI | [../research/devops-ai-platforms/gitlab-devops-ai/README.md](../research/devops-ai-platforms/gitlab-devops-ai/README.md) | DevOps / 生命周期平台 | 单一平台内的端到端研发链路便于形成统一状态模型 | 逻辑视图、进程视图 | 概念借鉴 | 待回读 |
| Atlassian Rovo / Teamwork Graph | [../research/devops-ai-platforms/atlassian-devops-ai/README.md](../research/devops-ai-platforms/atlassian-devops-ai/README.md) | 协作图谱 / 企业搜索 | 组织知识图谱和工作项关系图可支撑上下文召回 | 逻辑视图 | 模式复用 | 待回读 |
| ServiceNow AI | [../research/devops-ai-platforms/servicenow-devops-ai/README.md](../research/devops-ai-platforms/servicenow-devops-ai/README.md) | ITSM / 流程治理 | 流程、审批、工单和自动化执行需要强治理和审计 | 进程视图、物理视图 | 模式复用 | 待回读 |
| Coze 平台 | [../research/coze-platform/README.md](../research/coze-platform/README.md) | Agent Builder | 可视化 Agent 构建、插件接入和发布体验值得参考 | 开发视图、场景视图 | 概念借鉴 | 待回读 |
| Salesforce AgentForce | [../research/salesforce-agentforce/README.md](../research/salesforce-agentforce/README.md) | 企业业务 Agent | 业务对象、权限和流程上下文是企业 Agent 落地关键 | 逻辑视图、场景视图 | 概念借鉴 | 待回读 |
| Agentic Workbench 参考实践 | [../research/agentic-workbench-reference-patterns/README.md](../research/agentic-workbench-reference-patterns/README.md) | 独立参考体系 | 按产品心智、Agent 内核、编排可靠性、记忆技能、IDE 容器分层吸收参考项目 | 全部视图 | 模式复用 | 已初评 |
| Qoder / Quest | [Quest Mode](https://docs.qoder.com/zh/user-guide/quest-mode), [Experts Mode](https://docs.qoder.com/zh/user-guide/chat/experts-mode) | AI IDE / 自主任务 | Spec/Plan -> Review -> Execute -> Accept，复杂任务升级、简单任务轻量 | 场景视图、进程视图 | 模式复用 | 已初评 |
| A2UI | [google/A2UI](https://github.com/google/A2UI), [a2ui.org](https://a2ui.org/) | Agent-to-User Interface | 声明式 JSON UI、可信 catalog、增量 surface 更新、Agent action contract，可参考动态审批表单和右侧上下文卡片 | 场景视图、开发视图 | 模式复用 | 已初评 |
| OpenCode | [sst/opencode](https://github.com/sst/opencode) | 单 Agent 内核 / Coding Agent | 计划模式、执行模式、终端/桌面/协议分离、内置 subagent | 开发视图、进程视图 | 模式复用 | 已初评 |
| claude-code-rust | [lorryjovens-hub/claude-code-rust](https://github.com/lorryjovens-hub/claude-code-rust) | 单 Agent 内核参考 | ToolRegistry、AgentDefinition、ContextWindow、Hook、Plugin lifecycle；使用前需许可证和安全评估 | 开发视图 | 概念借鉴 | 已初评 |
| OpenClaw | [openclaw/openclaw](https://github.com/openclaw/openclaw), [docs](https://docs.openclaw.ai) | Local-first Gateway / 多通道 Agent 控制面 | Gateway 控制面、多 Agent 路由、session/task ledger、skills/plugin、sandbox/exec approvals、Canvas/A2UI、多端节点 | 全部视图 | 模式复用 | 已初评 |
| Open Multi-Agent | [JackChen-me/open-multi-agent](https://github.com/JackChen-me/open-multi-agent) | TypeScript 多 Agent 编排 | goal-to-task-DAG、coordinator 自动拆解、并行独立任务、onProgress/onTrace、HTML DAG dashboard、MCP 工具接入 | 进程视图、开发视图 | 模式复用 | 已初评 |
| Hermes Agent | [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent), [docs](https://hermes-agent.nousresearch.com/docs/) | 自学习 Agent / 自动化网关 | 技能自动沉淀、跨会话记忆、cron/webhook 自动化、多渠道交付、远程执行环境、Atropos 评测训练 | 场景视图、开发视图、进程视图、物理视图 | 模式复用 | 已初评 |
| Temporal | [temporalio/temporal](https://github.com/temporalio/temporal) | 工作流可靠性 | checkpoint、恢复、失败重试、长链路状态管理 | 进程视图、物理视图 | 模式复用 | 已初评 |
| LangGraph / deer-flow | [langchain-ai/langgraph](https://github.com/langchain-ai/langgraph), [bytedance/deer-flow](https://github.com/bytedance/deer-flow) | DAG / Agent harness | 任务图、Middleware、循环检测、子任务调度、skills、sandbox、memory | 进程视图、开发视图 | 模式复用 | 已初评 |
| ClawTeam | [HKUDS/ClawTeam](https://github.com/HKUDS/ClawTeam) | Agent Swarm Intelligence | 自组织团队、任务拆分、实时共享、汇总收敛，可参考右侧智能体协作与团队任务分派 | 逻辑视图、进程视图 | 模式复用 | 已初评 |
| crewAI / Mailbox 类模式 | [crewaiinc/crewai](https://github.com/crewaiinc/crewai), [OpenClaw multi-agent docs](https://docs.openclaw.ai/concepts/multi-agent) | 多 Agent 协作 | Crew/Flow、EventBus、Mailbox、Transport 等团队协作模式 | 逻辑视图、进程视图 | 模式复用 | 已初评 |
| mem0 / MAGMA / OpenSpace / OpenHarness | [mem0ai/mem0](https://github.com/mem0ai/mem0), [MAGMA](https://arxiv.org/abs/2601.03236), [HKUDS/OpenSpace](https://github.com/HKUDS/OpenSpace), [HKUDS/OpenHarness](https://github.com/HKUDS/OpenHarness) | 记忆与技能演化 | 语义记忆、技能记忆、工作记忆、执行追踪四类正交划分 | 逻辑视图、开发视图 | 模式复用 | 已初评 |
| 开源 Agent 编排框架 | 待用户继续提供源码或仓库 | Agent 编排 | 任务图、工具调用、记忆、人工确认、多 Agent 协作 | 进程视图、开发视图 | 待定 | 待登记 |
| 开源工作流/流程引擎 | 待用户继续提供源码或仓库 | 工作流引擎 | 状态机、重试、补偿、可视化流程、长事务 | 进程视图、物理视图 | 待定 | 待登记 |
| 开源开发者门户/平台工程框架 | 待用户提供源码或仓库 | 平台工程 | 服务目录、插件化、权限、团队视图、工具入口聚合 | 逻辑视图、开发视图 | 待定 | 待登记 |
| 开源知识库/RAG 框架 | 待用户提供源码或仓库 | 知识与上下文 | 文档索引、上下文召回、权限过滤、来源引用 | 逻辑视图、开发视图 | 待定 | 待登记 |

## 初始经验提炼

### 1. AgentCenter 不能只是聊天框

企业场景里的 AI 入口必须绑定真实对象：需求、服务、代码、构建、部署、告警、工单、审批和知识库。当前首页中心是对话工作台，但左右两侧和顶部/底部面板必须持续承载上下文、流程和历史，否则产品会退化成普通问答界面。

### 2. 上下文图谱是核心资产

多个参考方向都指向同一件事：Agent 的能力上限取决于它能拿到多少可信上下文。AgentCenter 后续需要把会话、任务、服务、环境、智能体、工具、执行记录和知识资产沉淀为统一对象关系。

### 3. 工作流比单次调用更重要

真实企业流程通常包含审批、等待、重试、回滚、人工确认和跨系统状态同步。后续评估开源框架时，要重点看它是否支持长流程、可恢复执行、幂等、审计和可观测性。

### 4. 引入外部框架要先隔离边界

开源 Agent 框架可以参考，但不应直接绑死 AgentCenter 的领域模型。更稳妥的方式是先通过适配器层接入，让核心对象模型、权限、审计和状态机保持在 AgentCenter 内部。

### 5. 产品引入要区分“能力”和“表达”

有些项目值得学习交互表达，有些项目值得学习运行时机制，有些只适合作为竞品定位参考。登记时要明确它影响的是产品体验、架构机制、开发实现，还是商业包装。

### 6. 参考项目要拆层吸收

不要把某个外部项目整体搬成 AgentCenter 的核心模型，而要拆成产品心智、单 Agent 内核、多 Agent 编排、可靠性基础设施、记忆技能、IDE 容器等层分别吸收。这样可以避免 Qoder、Temporal、LangGraph、mem0 等项目的概念语言混进同一个字段或同一套 UI 文案。

## 后续源码评估流程

当你把开源框架代码或仓库材料丢进来时，建议按下面流程处理：

1. 建立条目：在“当前参考登记”里添加项目名称、来源和分类。
2. 快速阅读：先看 README、架构图、核心目录、许可证和运行方式。
3. 代码定位：找出真正影响我们的模块，例如 planner、executor、tool registry、memory、workflow、connector、observability。
4. 映射 4+1：判断它主要影响哪张视图。
5. 做引入判断：概念借鉴、模式复用、适配器集成、直接依赖或拒绝引入。
6. 需要时产出 ADR：如果会影响关键选型或长期依赖，新增架构决策记录。

## 引入判断标准

| 标准 | 需要关注的问题 |
|------|----------------|
| 产品匹配度 | 是否服务 AgentCenter 的企业工作台定位，而不是只解决单点 Demo？ |
| 架构匹配度 | 是否能融入我们的领域模型、任务生命周期和工具边界？ |
| 企业可用性 | 是否支持权限、审计、隔离、配置、可观测性和错误恢复？ |
| 代码质量 | 模块边界是否清晰，是否容易测试、替换和维护？ |
| 许可证风险 | 是否允许商业使用、二次分发和企业内集成？ |
| 运行复杂度 | 是否引入过重依赖、额外服务或难维护运行时？ |
| 数据控制 | 是否会把上下文、日志、密钥或企业数据带出边界？ |
| 可替换性 | 以后不合适时，是否能通过适配器替换？ |

## 需要沉淀的产物

| 产物 | 位置 | 触发条件 |
|------|------|----------|
| 参考登记 | 本文档 | 新增开源项目、竞品材料或调研来源 |
| 详细调研 | `docs/research/` | 某个项目值得展开分析 |
| 原型验证 | `agent-center-demo/` 或 `docs/prototype/` | 某个能力需要通过界面或交互验证 |
| 架构决策 | `docs/architecture/decisions/` | 影响关键依赖、模块边界或长期路线 |
| 4+1 更新 | `docs/architecture/ARCHITECTURE-OVERVIEW.md` | 参考经验改变系统结构或运行方式 |
