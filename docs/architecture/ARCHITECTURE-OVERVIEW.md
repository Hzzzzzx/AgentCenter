# AgentCenter 系统架构设计 — 4+1 视图

> 生成时间：2026-04-02
> 状态：设计讨论中
> 关联文档：[应用架构基线](./APPLICATION-ARCHITECTURE-BASELINE.md) | [统一对象模型](./UNIFIED-DOMAIN-MODEL.md) | [环境与晋升](./ENVIRONMENT-AND-PROMOTION.md) | [AI 原生流程](./AI-NATIVE-DEVELOPMENT.md)

---

## 当前讨论基线

2026-04-27 之后的 4+1 架构讨论以 [APPLICATION-ARCHITECTURE-BASELINE.md](./APPLICATION-ARCHITECTURE-BASELINE.md) 为前置基线。本文中的具体技术栈、消息队列、存储、中间件和部署形态如未被后续 ADR 明确确认，都只视为早期草案或示例，不构成当前选型结论。

当前先固定：

- 统一上下文平台：证据、关系、索引、记忆、上下文构建和权限过滤。
- 多项目多用户模型：Tenant、Workspace、Project、User、Team、Role、Agent、Run。
- 运行事实层：Run、Plan、Step、ToolCall、Approval、Artifact、Event。
- 企业治理：IAM、ACL、Policy、Approval、Audit、Sandbox、Secret Vault。
- 性能并发：前台对话、后台任务、索引同步、投影查询分链路设计。

## 一、架构全景

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           AgentCenter 全景                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│     "我需要一个新功能" ──▶ AI 理解 ──▶ 自动开发 ──▶ 自动测试 ──▶ 自动部署    │
│                                                                                  │
│           ┌──────────────────────────────────────────────────────────────┐       │
│           │                                                               │       │
│           │    ┌─────────────────────────────────────────────────────┐  │       │
│           │    │           全周期云端开发平台                          │  │       │
│           │    └─────────────────────────────────────────────────────┘  │       │
│           │                            │                                 │       │
│           │         ┌─────────────────┼─────────────────┐              │       │
│           │         ▼                 ▼                 ▼              │       │
│           │   ┌───────────┐    ┌───────────┐    ┌───────────┐          │       │
│           │   │  云端 IDE │    │  DevOps   │    │  AI 助手  │          │       │
│           │   │           │    │   全流程   │    │           │          │       │
│           │   │  • 代码   │    │  • 计划   │    │  • 对话   │          │       │
│           │   │  • 调试   │    │  • 开发   │    │  • 编排   │          │       │
│           │   │  • 预览   │    │  • 构建   │    │  • 自动   │          │       │
│           │   │  • 终端   │    │  • 部署   │    │  • 诊断   │          │       │
│           │   │  • 协作   │    │  • 监控   │    │  • 自愈   │          │       │
│           │   └───────────┘    └───────────┘    └───────────┘          │       │
│           │                                                               │       │
│           └───────────────────────────────────────────────────────────────┘       │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、视图 1: 逻辑视图 (Logical View)

**关注点**：功能分解、服务边界、数据结构

```
┌──────────────────────────────────────────────────────────────────────────┐
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │  Builder    │  │  Deployer   │  │  Monitor    │  │  Remediat   │   │
│  │  Agent      │  │  Agent      │  │  Agent      │  │  Agent      │   │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘   │
│         │                │                │                │           │
│  ┌──────┴────────────────┴────────────────┴────────────────┴──────┐   │
│  │                      Agent Registry Service                      │   │
│  │              (元数据管理 / 健康检查 / 能力注册)                    │   │
│  └───────────────────────────┬────────────────────────────────────┘   │
│                              │                                          │
│  ┌───────────────────────────┴────────────────────────────────────┐   │
│  │                     Orchestration Engine                          │   │
│  │              (任务分解 / 路由 / 依赖管理 / 状态机)                  │   │
│  └───────────────────────────┬────────────────────────────────────┘   │
│                              │                                          │
│  ┌───────────────────────────┴────────────────────────────────────┐   │
│  │                       Message Bus (Kafka/Pulsar)                  │   │
│  │              (异步通信 / 事件驱动 / 可靠投递)                      │   │
│  └───────────────────────────┬────────────────────────────────────┘   │
│                              │                                          │
│  ┌──────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │   │
│  │ Identity │  │   Audit     │  │  Policy     │  │   Notification  │ │   │
│  │ Service  │  │   Log       │  │  Engine     │  │     Service     │ │   │
│  └──────────┘  └─────────────┘  └─────────────┘  └─────────────────┘ │   │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                        Data Layer                                 │  │
│  │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐    │  │
│  │  │Artifact│  │State   │  │Metrics │  │ Logs   │  │ Secrets│    │  │
│  │  │Store   │  │Store   │  │ DB     │  │ Archive│  │ Vault  │    │  │
│  │  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

### Agent 类型与职责

| Agent | 职责 |
|-------|------|
| Builder Agent | 代码构建、测试、制品生成 |
| Deployer Agent | 部署、回滚、环境管理 |
| Monitor Agent | 指标采集、告警触发 |
| Remediation Agent | 故障自愈、告警处理 |
| Notifier Agent | 消息推送、通知管理 |
| Code Agent | 代码生成、重构、Code Review |
| Test Agent | 测试执行、覆盖率分析 |
| Log Agent | 日志汇聚、分析、搜索 |

---

## 三、视图 2: 开发视图 (Development View)

**关注点**：代码组织、模块划分、技术栈

```
agent-center/                         # Monorepo
├── apps/
│   ├── web-ide/                     # 云端 IDE 前端 (Monaco Editor)
│   ├── dashboard/                   # DevOps 控制台 (气泡墙/仪表盘)
│   └── chat-ui/                     # AI 对话界面
│
├── services/
│   ├── ide-service/                 # IDE 核心服务 (工作空间/执行/调试)
│   ├── orchestration/               # 编排服务 (对话引擎/流程编排/Agent 运行时)
│   ├── pipeline/                    # 流水线服务 (计划/代码/构建/测试/部署/监控 Agent)
│   └── integrations/                # 集成服务 (代码托管/K8s/云厂商/外部工具 适配器)
│
├── agents/                          # Agent SDK
├── libs/                            # 共享库 (公共类型/消息/指标/追踪/AI 核心)
└── infra/                           # 基础设施 (K8s/Terraform/Helm)
```

### 技术栈

| 层级 | 技术选型 |
|------|----------|
| 前端 | React 18 + TypeScript + TailwindCSS + Monaco Editor |
| 后端 | Go 1.22+ (高性能服务) + Rust (关键组件) |
| AI | 多模型 (OpenAI/Claude/本地 LLM) |
| 运行时 | Kubernetes 1.29+ + Docker + containerd |
| 消息 | Kafka 3.6+ + Redis 7+ |
| 存储 | PostgreSQL 16+ + S3 + Redis |

---

## 四、视图 3: 进程视图 (Process View)

**关注点**：运行时行为、并发模型、消息流

### 请求处理流程

```
Client ──▶ API Gateway ──▶ Auth ──▶ Orchestration Engine
                                      │
                                      ▼
                              ┌─────────────┐
                              │  Task Queue  │
                              │   (Kafka)    │
                              └──────┬──────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
       ┌──────────┐          ┌──────────┐          ┌──────────┐
       │ Builder  │          │ Deployer │          │ Monitor  │
       │ Agent Pool│          │ Agent Pool│          │ Agent Pool│
       └─────┬─────┘          └─────┬─────┘          └─────┬─────┘
             └──────────────────────┼──────────────────────┘
                                    ▼
                           ┌─────────────────┐
                           │  Result Topics  │
                           │    (Kafka)      │
                           └────────┬────────┘
                                    │
                                    ▼
                           ┌─────────────────┐
                           │  State Store    │
                           │ (PostgreSQL)    │
                           └─────────────────┘
```

### Agent 生命周期

```
┌────────┐    Register    ┌────────┐    Heartbeat    ┌──────┐
│ Start  │ ─────────────►  │ Idle   │ ─────────────►  │ Busy │
└────────┘                └────────┘                └──────┘
     │                        ▲                       │
     │ Healthcheck            │ Complete/             │
     │ Failed                 │ Fail                  │
     ▼                        └───────────────────────┘
┌────────┐
│Offline │◄──────── Timeout (30s no heartbeat)
└────────┘
```

### 消息 Topic 划分

| Topic | 用途 |
|-------|------|
| `agent.tasks.{type}` | 任务分发 |
| `agent.results.{agentId}` | 结果回传 |
| `agent.events` | 生命周期事件 |
| `agent.metrics` | 指标数据 |
| `agent.logs` | 日志汇集 |

---

## 五、视图 4: 物理视图 (Physical View)

**关注点**：部署拓扑、跨地域、高可用

```
                    ┌─────────────────────┐
                    │   Global CDN        │
                    │  (静态资源/加速)    │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Global Load Bal   │
                    └──────────┬──────────┘
                               │
       ┌───────────────────────┼───────────────────────┐
       │                       │                        │
       ▼                       ▼                        ▼
┌──────────────┐       ┌──────────────┐        ┌──────────────┐
│  Asia        │       │  Americas    │        │  EMEA        │
│  Region      │       │  Region      │        │  Region      │
│              │       │              │        │              │
│ K8s Cluster  │       │ K8s Cluster  │        │ K8s Cluster  │
│ - IDE Front  │       │ - IDE Front  │        │ - IDE Front  │
│ - IDE Back   │       │ - IDE Back   │        │ - IDE Back   │
│ - Workspace  │       │ - Workspace  │        │ - Workspace  │
│   Pool       │       │   Pool       │        │   Pool       │
│ - AI Agent   │       │ - AI Agent   │        │ - AI Agent   │
│   Cluster    │       │   Cluster    │        │   Cluster    │
└──────┬───────┘       └──────┬───────┘        └──────┬───────┘
       │                       │                        │
       └───────────────────────┼────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Global Services    │
                    │  Kafka / PostgreSQL  │
                    │  Redis / Vault / S3  │
                    └─────────────────────┘
```

### 高可用设计

| 组件 | 副本策略 | 故障转移 | RTO | RPO |
|------|----------|----------|-----|-----|
| API Gateway | 3x per region | DNS failover | < 30s | N/A |
| Orchestration | 5x active-active | None needed | < 5s | < 1s |
| Agent Registry | 3x per region | Multi-region read | < 30s | < 1s |
| Kafka | 3x replication | Auto ISR | < 30s | < 1s |
| PostgreSQL | Primary + 2 Standby | 自动切换 | < 60s | < 1s |

---

## 六、视图 5: 场景视图 (Scenarios)

### 场景 1: 新 Agent 注册

```
Agent → GET /auth/token → Center
Agent → POST /agents/register {type, capabilities, region} → Registry
Agent → Subscribe to tasks.{type}
Agent → Start heartbeat (30s interval)
Center → ACK registration
```

### 场景 2: 任务执行

```
User → Create Task → Orchestration → Store metadata
Orchestration → Publish to Kafka → Agent Pool
Agent Pool → Route by capabilities → Assign least loaded agent
Agent → Execute task → Stream logs → Return result
Orchestration → Update task status → Notify user
```

### 场景 3: 故障恢复

```
Agent heartbeat timeout (>30s) → Registry mark offline
Registry → Find pending tasks for offline agent
Registry → Re-queue tasks to available agents
New agent picks up → Executes → Reports result
Original agent recovers → Reconnects → Marked active
```

---

## 七、核心设计原则

| 原则 | 说明 |
|------|------|
| **能力抽象** | 用功能能力名称代替具体产品名，平台不绑定任何外部系统 |
| **模型统一** | 核心对象共用统一模型，全局唯一 ID |
| **适配隔离** | 存量系统通过适配器接入，隔离差异性 |
| **事件驱动** | 异步解耦，适配器将外部事件转换为统一事件格式 |
| **环境强隔离** | 多层环境严格分离，晋升单向不可逆 |
| **渐进迁移** | 不推翻存量系统，先接入后逐步替代 |
| **AI 叠加** | 在统一模型基础上叠加 AI 能力 |

---

## 八、视图总结

| 视图 | 关注者 | 核心内容 |
|------|--------|----------|
| **逻辑视图** | 开发者、架构师 | 功能分解、服务边界、数据模型 |
| **开发视图** | 开发团队 | 代码组织、模块依赖、技术栈 |
| **进程视图** | SRE、运维 | 运行时行为、并发模型、消息流 |
| **物理视图** | 基础设施团队 | 部署拓扑、多 Region、高可用 |
| **场景视图** | 所有利益相关者 | 关键流程、故障恢复、用户体验 |
