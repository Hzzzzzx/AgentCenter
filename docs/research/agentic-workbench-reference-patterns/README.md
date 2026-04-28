# Agentic Workbench 参考实践

> 状态：独立参考基线
> 最近更新：2026-04-27
> 用途：沉淀 AgentCenter 后续产品和架构设计可复用的开源项目经验、公开链接和引入判断。

## 设计原则

本文档不依赖任何本地项目路径，也不要求读者拥有某个历史工作区。它只保留可以迁移到 AgentCenter 的经验：

1. 公开项目使用 GitHub 仓库或官方文档链接。
2. 经验沉淀为 AgentCenter 自己的架构语言。
3. 外部项目只提供参考，不作为 AgentCenter 的 source of truth。
4. 引入前先判断影响的是产品体验、运行时内核、编排可靠性、记忆系统，还是部署治理。

## 分层吸收模型

AgentCenter 不应该整体照搬某个 Agent 框架。更稳妥的方式是按层吸收：

```text
产品心智与主交互节奏     <- Qoder / Quest / Cursor / Devin
单 Agent 内核与工具边界  <- Claude Code / OpenCode / claude-code-rust
多 Agent 编排与事件      <- ClawTeam / crewAI / OpenClaw mailbox patterns / LangGraph / deer-flow
长流程可靠性             <- Temporal / lobster
记忆与经验沉淀           <- mem0 / MAGMA / OpenSpace / OpenHarness
自学习与自动化网关       <- Hermes Agent
本地网关与多端控制面     <- OpenClaw
桌面与 IDE 容器          <- VS Code / Tauri / Skales / OpenCode Desktop
```

核心判断：

> 先抽取优秀实践，再组合成 AgentCenter 自己的产品和运行时模型。不要把不同参考项目的概念体系混进同一字段、同一张表或同一套 UI 文案。

## P0 参考项目

| 项目 | 链接 | 核心启发 | AgentCenter 落点 | 4+1 视图 |
|------|------|----------|------------------|----------|
| Qoder Quest / Experts | [Quest Mode](https://docs.qoder.com/zh/user-guide/quest-mode), [Experts Mode](https://docs.qoder.com/zh/user-guide/chat/experts-mode) | `Spec/Plan -> Review -> Execute -> Accept` 的任务主路径；简单任务轻量，复杂任务升级 | 对话工作台的轻重路由、计划审查、执行反馈 | 场景视图、进程视图 |
| A2UI | [google/A2UI](https://github.com/google/A2UI), [a2ui.org](https://a2ui.org/) | Agent-to-User Interface；Agent 输出声明式 JSON UI，由客户端可信 catalog 渲染 | 动态审批表单、右侧协作卡片、流程状态面板、结果可视化；避免 Agent 直接生成前端代码 | 场景视图、开发视图 |
| OpenCode | [sst/opencode](https://github.com/sst/opencode) | 终端/桌面/协议分离，内置 build/plan agent，工具执行边界清晰 | 单 Agent 内核、计划模式、远程/本地执行通道 | 开发视图、进程视图 |
| claude-code-rust | [lorryjovens-hub/claude-code-rust](https://github.com/lorryjovens-hub/claude-code-rust) | Tool trait、ToolRegistry、AgentDefinition、ContextWindow、Hook、Plugin lifecycle | 工具注册、上下文窗口、权限、生命周期扩展参考；使用前需做许可证和安全评估 | 开发视图 |
| OpenClaw | [openclaw/openclaw](https://github.com/openclaw/openclaw), [docs](https://docs.openclaw.ai) | Local-first Gateway 控制面；多通道接入、多 Agent 路由、session/task ledger、skills/plugin、sandbox、Canvas/A2UI | AgentCenter 的运行时控制面、会话隔离、后台任务台账、技能/插件治理、沙箱策略、多端 surface 参考 | 逻辑视图、进程视图、开发视图、物理视图、场景视图 |
| Open Multi-Agent | [JackChen-me/open-multi-agent](https://github.com/JackChen-me/open-multi-agent) | TypeScript-native goal-to-task-DAG；coordinator 自动拆解目标、并行独立任务、合成结果 | AgentCenter 后端编排层、任务 DAG、团队执行流、进度/trace/DAG dashboard | 进程视图、开发视图 |
| Hermes Agent | [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent), [docs](https://hermes-agent.nousresearch.com/docs/) | 自学习 AI Agent；技能自动沉淀、跨会话记忆、cron/webhook 自动化、多渠道 gateway、远程执行环境 | AgentCenter 的技能学习闭环、自动化触发、跨渠道交付、远程执行治理、评测训练环境参考 | 场景视图、开发视图、进程视图、物理视图 |
| ClawTeam | [HKUDS/ClawTeam](https://github.com/HKUDS/ClawTeam) | Agent Swarm Intelligence；自组织团队、任务拆分、实时共享、汇总收敛 | 右侧智能体协作面板、团队任务分派、交接队列、协作状态回放 | 逻辑视图、进程视图 |
| crewAI | [crewaiinc/crewai](https://github.com/crewaiinc/crewai) | Crew 与 Flow 两类抽象；多 Agent 协作和事件驱动流程 | 智能体团队协作、角色分工、任务委托 | 逻辑视图、进程视图 |
| deer-flow | [bytedance/deer-flow](https://github.com/bytedance/deer-flow) | 长任务 harness、sub-agents、skills、sandbox、memory、context engineering | 复杂任务拆解、子 Agent 并行、沙箱和技能体系 | 进程视图、开发视图 |
| LangGraph | [langchain-ai/langgraph](https://github.com/langchain-ai/langgraph) | 将 Agent 状态和控制流表达为 graph，适合循环、分支和恢复 | 内部任务图、循环检测、状态转移，不直接暴露给用户 | 进程视图 |
| Temporal | [temporalio/temporal](https://github.com/temporalio/temporal) | Durable execution、retry、checkpoint、long-running workflow | 长流程可靠执行、暂停恢复、失败重试 | 进程视图、物理视图 |
| Lobster | [openclaw/lobster](https://github.com/openclaw/lobster), [OpenClaw Lobster docs](https://docs.openclaw.ai/tools/lobster) | typed workflow shell、approval checkpoints、resume token、deterministic pipeline | 可审批、可恢复、可审计的工具链工作流 | 进程视图、物理视图 |
| mem0 | [mem0ai/mem0](https://github.com/mem0ai/mem0) | AI Agent 的通用记忆层和长期记忆生命周期 | 用户/会话/Agent 记忆，长期上下文沉淀 | 逻辑视图、开发视图 |
| OpenSpace | [HKUDS/OpenSpace](https://github.com/HKUDS/OpenSpace) | SkillRecord、SkillLineage、技能进化和执行效果反馈 | 技能使用记录、技能派生、经验沉淀 | 开发视图、场景视图 |
| OpenHarness | [HKUDS/OpenHarness](https://github.com/HKUDS/OpenHarness) | tools / skills / plugins / providers / multi-agent / testing 等 harness 边界 | AgentCenter 插件、技能、Provider、trace 和权限模块边界 | 开发视图、物理视图 |

## P1 参考项目

| 项目 | 链接 | 核心启发 | AgentCenter 落点 |
|------|------|----------|------------------|
| Microsoft Agent Framework | [microsoft/agent-framework](https://github.com/microsoft/agent-framework) | Agent 与 Workflow 的区分；Python/.NET 多 Agent 工作流 | 区分智能体能力、流程编排和部署形态 |
| Skales | [skalesapp/skales](https://github.com/skalesapp/skales) | 本地桌面 Agent、Skill 标准、个人工作台 | 桌面形态和单用户工作台体验参考 |
| oh-my-opencode | [opensoft/oh-my-opencode](https://github.com/opensoft/oh-my-opencode) | OpenCode 插件化、多 Agent、专业 agent 配置 | 作为多 Agent coding harness 的可选参考 |
| OpenClaw Mission Control | [abhi1693/openclaw-mission-control](https://github.com/abhi1693/openclaw-mission-control) | Agent 运营面板、生命周期、审批治理、活动可视化 | 右侧协作面板、状态栏、运维治理参考 |
| edict | [cft0808/edict](https://github.com/cft0808/edict) | 多 Agent 编排、状态机、权限矩阵、审计追踪 | 权限、审批、审计和组织化 Agent 参考 |
| MAGMA | [arXiv:2601.03236](https://arxiv.org/abs/2601.03236) | 多图记忆架构和策略引导检索 | 可解释上下文召回，后续记忆系统专项参考 |

## 可迁移经验

### 1. 对话工作台要有轻重路由

AgentCenter 首页中心是对话工作台，但对话不等于纯聊天。建议将请求分为三类：

| 类型 | 用户感知 | 系统行为 |
|------|----------|----------|
| 轻问答 | 快速回答、解释、判断 | 单轮或短链路，不展开重型面板 |
| 单 Agent 执行 | 读代码、调用工具、产出结果 | 展示工具调用、状态和结果 |
| 重型任务 | 计划、审查、执行、回放 | 顶部流程、右侧协作、底部历史联动展开 |

### 2. 事实层和投影层必须分开

AgentCenter 后续不应把聊天消息当成完整审计源。

```text
事实层：run / step / turn / tool_call / output_chunk / file_change
投影层：chat_messages / timeline cards / status chips / activity rows
```

原则：

- UI 是事实的投影，不是事实本身。
- replay 依赖事实层重建，不依赖运行时内存。
- 审批、恢复、审计都要绑定事实层。

### 3. Workflow 和 Pipeline 要分层

建议保持两层概念：

```text
Workflow 静态蓝图 -> Pipeline 动态执行 -> Stage -> Task -> Agent
```

这样首页顶部“全流程阶段”可以对应运行时实例，同时架构上仍然保留可复用的流程模板。

### 4. 单 Agent 内核优先于多人编排

多 Agent 编排的前提是单 Agent 能可靠工作：

1. ToolRegistry 和工具 schema 清晰。
2. 权限、路径、命令、网络边界可控。
3. 上下文窗口可压缩、可驱逐、可摘要。
4. tool loop 有最大迭代、错误恢复和可观测记录。
5. 再做 sub-agent、team、wave、handoff。

### 5. 记忆系统按职责正交划分

不要只做一个泛泛的 `memory`。

| 记忆类型 | 语义 | 典型内容 |
|----------|------|----------|
| 语义记忆 | 知道什么 | 项目知识、术语、架构决策、API 规范 |
| 技能记忆 | 知道怎么做 | Skill、工具偏好、流程模板、Prompt 模式 |
| 工作记忆 | 当前正在处理什么 | 当前任务状态、阻塞点、近期结论、上下文胶囊 |
| 执行追踪 | 过去做了什么 | agent turns、tool calls、命令记录、文件变化 |

### 6. 外部概念不能直接污染内部模型

外部项目的名词可以出现在调研文档里，但不要直接成为核心表字段或 UI 主文案。AgentCenter 应该有自己的稳定语言：

- 产品文案：用户能理解的任务、流程、协作、历史。
- 运行时结构：run、step、turn、tool_call。
- 审计事实：append-only events、file changes、approvals。
- 记忆资产：semantic memory、skill memory、working memory、execution trace。

### 7. 目标驱动的运行时 DAG 值得吸收

Open Multi-Agent 提供了一个很适合 AgentCenter 的中间形态：用户只描述目标，运行时 coordinator 自动拆解为任务 DAG，并并行执行无依赖任务。它和 LangGraph 的差异也很清楚：LangGraph 更偏 graph-first，Open Multi-Agent 更偏 goal-first。

AgentCenter 可吸收：

- `runTeam(team, goal)` 这种目标驱动入口，适合中心对话工作台。
- `runTasks()` 这种显式任务图入口，适合后台流程模板或高级编排。
- `AgentPool.runParallel()` 这种 fan-out / aggregate 模式，适合右侧智能体协作。
- `onProgress` 和 `onTrace` 分层，分别支撑前端实时状态和后端可观测性。
- post-run HTML DAG dashboard 的思路，可迁移为 AgentCenter 的执行回放/证据页。
- `MemoryStore` 可替换接口，可作为后续 Redis/Postgres/企业知识库适配参考。
- `toolPreset + allowlist + denylist + MCP` 的工具边界，可用于 Agent 权限模型。

需要保留边界：

- 它明确不做持久化 checkpoint，适合秒到分钟级任务；AgentCenter 的企业长流程仍需要 Temporal/Lobster 类可靠执行能力。
- 它是 TypeScript/Node 运行时参考，不等于 AgentCenter 后端最终依赖选型。
- 它的自动 DAG 拆解不应直接成为用户文案，用户看到的是任务阶段、协作状态和可审查结果。

### 8. Agent 生成 UI 应该走声明式协议

A2UI 给 AgentCenter 提供了一个很重要的 UI 边界：Agent 不应该直接生成可执行前端代码，而应该生成声明式 JSON，由工作台用可信组件 catalog 渲染。

可吸收点：

- **安全边界**：Agent 只能请求渲染 catalog 中预批准的组件，而不是任意 HTML/JS。
- **增量更新**：使用 flat component list + ID references，适合流式生成和局部更新。
- **多端可移植**：Agent 输出 UI 意图，Web/Flutter/React/其他客户端各自映射到本地组件。
- **数据模型同步**：输入组件本地同步状态，正式 action 才把必要上下文发回 Agent。
- **Action 分层**：本地 function 处理即时 UI 行为，event 才发回 Agent 处理业务动作。
- **自定义 catalog**：AgentCenter 可以定义自己的工作台组件，例如 `ApprovalCard`、`WorkflowStagePanel`、`AgentHandoffCard`、`RiskSummary`、`ExecutionTraceView`。

对 AgentCenter 的建议：

- 先把 A2UI 作为协议/模式参考，不急着直接引入依赖。
- 后续可以设计 `AgentCenter UI Catalog`，限制 Agent 只能生成符合工作台设计系统的面板。
- 中心对话区、右侧上下文面板、顶部流程详情、底部历史详情都可以成为可渲染 surface。
- A2UI 规格仍在 Public Preview，正式落地前需要锁版本、做兼容策略和安全审查。

### 9. 自学习 Agent 要把记忆、技能和自动化拆开

Hermes Agent 对 AgentCenter 的启发不在于照搬一个 CLI，而在于它把“Agent 如何越用越懂项目”拆成了几个可独立治理的能力：跨会话记忆、技能创建与改进、定时/事件触发、消息渠道交付、远程执行环境，以及面向训练评测的轨迹采集。

可吸收点：

- **学习闭环**：复杂任务后把经验沉淀为技能，并在使用中继续改进，适合 AgentCenter 后续的团队级 skill memory。
- **记忆 nudges**：系统主动提醒 Agent 持久化重要知识，而不是完全依赖用户手动整理。
- **自动化触发**：cron、GitHub webhook、API trigger 可以抽象为 `trigger -> context builder -> agent run -> delivery`。
- **多渠道交付**：Telegram、Discord、Slack、WhatsApp、Signal、Email 等能力说明“结果交付”应该独立于“运行工作台”。
- **远程执行环境**：local、Docker、SSH、Daytona、Singularity、Modal 等 backend 适合映射到 AgentCenter 的执行沙箱和算力策略。
- **模型无锁定**：provider/model 切换作为运行配置，而不是写死在任务模型里。
- **评测训练闭环**：Atropos environments、tool-calling loop、reward function、trajectory compression 可作为未来 Agent 质量评估和训练数据采集参考。

需要保留边界：

- AgentCenter 是企业 Web 工作台，不应直接变成个人 CLI 或消息机器人。
- 自动运行和远程执行必须进入权限、审批、审计、数据驻留和密钥治理，不宜默认开放。
- 技能自我改写要有版本、来源、评测、回滚和人工确认机制。
- Hermes 的 OpenClaw 迁移能力说明“外部工作区导入”很重要，但 AgentCenter 文档不绑定任何特定历史项目或本机路径。

### 10. Gateway 控制面要成为运行时边界

OpenClaw 的关键启发是把 Gateway 做成一个长期运行的控制面：它拥有 channel 连接、WebSocket API、agent run、session store、task ledger、cron/webhook、nodes、Canvas/A2UI，以及配置校验和安全诊断。对 AgentCenter 来说，这比单纯学习某个聊天 UI 更有价值。

可吸收点：

- **Gateway 作为控制面**：统一承载 sessions、channels、tools、events、health、cron 等能力，前端和节点通过 typed WebSocket/HTTP 访问。
- **Agent 与会话隔离**：一个 gateway 可以承载多个 agent，每个 agent 有自己的 workspace、state directory、auth profile、session store，再由 bindings 把通道/账号/会话路由过去。
- **Session 不是普通聊天记录**：DM、群组、cron、webhook、node 等来源有不同 session key；session transcript 与 session metadata 分开存储，便于回放、裁剪和权限控制。
- **后台任务台账**：ACP、subagent、cron、CLI 等 detached work 进入 `queued -> running -> terminal` 生命周期，适合作为 AgentCenter 底部历史/运行记录的数据模型参考。
- **队列与并发控制**：按 session lane 串行、按全局 lane 控制并发，避免同一会话内多个 agent run 互相踩状态。
- **Skills 和 plugins 分层**：skill 是可读的操作经验，plugin 是运行时扩展；ClawHub/manifest/allowlist/denylist 提供治理入口。
- **沙箱与审批分层**：sandbox、tool policy、elevated、exec approvals 各管不同风险层，不把“能调用工具”和“能碰宿主机”混成一个开关。
- **多端 surface**：WebChat、Control UI、macOS/iOS/Android nodes、Canvas/A2UI 说明 AgentCenter 后续也可以把工作台 surface 与运行控制面解耦。

需要保留边界：

- OpenClaw 是 personal assistant，AgentCenter 是企业 Web 工作台；我们借鉴运行时边界，不照搬个人通道产品形态。
- OpenClaw 主会话默认可以高权限运行宿主机工具；AgentCenter 企业场景必须默认最小权限、强审计和显式审批。
- OpenClaw 的 workspace 文件约定适合个人长期记忆；AgentCenter 还需要组织级对象模型、租户隔离、项目权限和数据生命周期。
- 直接依赖 OpenClaw 之前必须做许可证、供应链、运行复杂度、插件安全和数据驻留评估；当前定位是模式复用。

## 建议进入 AgentCenter 的首批专题

| 专题 | 推荐参考 | 目标产物 |
|------|----------|----------|
| 对话工作台任务主路径 | Qoder / Quest, OpenCode, Devin/Cursor 产品经验 | `场景视图` + 首页交互合同 |
| Agent-to-UI 协议 | A2UI, AG-UI/CopilotKit, AgentCenter 当前工作台组件 | 工作台可信 UI Catalog、动态 surface 协议、action contract |
| Gateway 控制面与多端 surface | OpenClaw, Skales, OpenCode Desktop | 控制面 API、Web 工作台、节点/外部通道、Control UI 边界 |
| 单 Agent 内核 | OpenCode, claude-code-rust, OpenHarness | ToolRegistry、Permission、ContextWindow、Hook 设计 |
| 多 Agent 协作 | OpenClaw, Open Multi-Agent, ClawTeam, crewAI, deer-flow, LangGraph | Run/Plan/Wave/Step/Subagent 模型、会话隔离和通道路由 |
| 长流程可靠执行 | Temporal, Lobster | checkpoint、resume token、审批、重试、审计 |
| 记忆与技能演化 | OpenClaw, Hermes Agent, mem0, MAGMA, OpenSpace, OpenHarness | 四类记忆模型、技能沉淀机制、技能版本与评测 |
| 自动化触发与交付 | OpenClaw, Hermes Agent, Lobster, Temporal | cron/webhook/API trigger、context builder、delivery channel、审批审计 |

## 引入判断

| 判断 | 建议 |
|------|------|
| 只影响产品表达 | 放入原型或场景视图，不改核心模型 |
| 影响运行时结构 | 进入 4+1 进程视图和 ADR |
| 影响数据模型 | 先写领域模型和迁移策略 |
| 影响依赖选型 | 必须补许可证、安全、部署复杂度评估 |
| 只适合远期想象 | 保留在 research，不进入实施计划 |
