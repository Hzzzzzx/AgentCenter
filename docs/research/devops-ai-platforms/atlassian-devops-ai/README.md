# Atlassian DevOps 领域 AI Agent 布局调研报告

> 调研时间：2026年3月 | 信息来源：Atlassian 官方文档及产品页面

---

## 一、Atlassian AI 战略总览

Atlassian 的 AI 战略以 **Rovo** 为核心品牌，以 **Teamwork Graph** 为数据智能层，以 **Forge** 为开发者扩展平台，构建覆盖研发、IT 运维、服务管理的全链路 AI 能力体系。

```
Atlassian AI Platform
├── Rovo (AI 应用层)
│   ├── Rovo Search — 企业级语义搜索
│   ├── Rovo Chat — AI 对话助手
│   ├── Rovo Agents — AI Agent（内置 + 自定义）
│   └── Rovo Studio — 低代码 Agent 构建平台
├── Teamwork Graph (数据智能层)
│   └── 统一连接 Jira/Confluence/Bitbucket/第三方 SaaS 数据
├── Forge (开发者平台)
│   ├── Forge LLMs — 原生 LLM 集成（EAP）
│   ├── Forge LLMs Web Trigger — LLM Web 触发器
│   └── Rovo Agent 模块 — 自定义 Agent 开发
└── Atlassian Intelligence (AI 信任与合规层)
    ├── 权限感知 / SOC 2 / ISO 27001
    └── 数据residency支持
```

---

## 二、Atlassian Forge + AI

### 2.1 Forge LLM 模块（EAP）

Forge 平台通过 `@forge/llm` 模块提供原生 LLM 集成，目前处于 Early Access Program (EAP) 阶段。

**核心能力：**

| 能力 | 说明 |
|------|------|
| **LLM Web Trigger** | 通过 Web 触发器接收用户请求并调用 LLM，支持同步/异步模式 |
| **Agentic LLM** | 支持 Tool Use 模式，LLM 可调用外部工具（函数）实现 agentic 行为 |
| **长时 LLM 处理** | 结合 Forge Realtime + Queue Consumer，实现最长 15 分钟的 LLM 任务，实时推送结果到 UI |
| **多模型支持** | 支持 Claude 等模型，可通过 `model` 参数指定 |

**关键文档路径：**
- `Create an LLM Web trigger application` — 基础 LLM 集成教程
- `Create an Agentic LLM Web trigger application` — Tool Use + Agentic 行为实现
- `Handling long-running LLM processes with Forge Realtime` — 长时任务架构

### 2.2 Forge 平台 AI 扩展能力

| 功能 | 说明 |
|------|------|
| **Forge LLM API** | 在 Forge 函数中调用 LLM，支持 `chat()` 函数 |
| **Realtime** | WebSocket 风格实时通信，用于 LLM 流式结果推送 |
| **Async Events + Queue** | 队列消费者可运行 15 分钟超时，用于长时 LLM 处理 |
| **Rovo Agent 模块** | 开发者可通过 `rovo:agent` 和 `action` 模块构建自定义 Agent |

**典型应用场景（Forge 示例应用）：**

| 示例应用 | 功能描述 |
|----------|----------|
| **Jira Issue Analyst** | 分析 Jira issue 队列，辅助研发团队梳理问题 |
| **Team Event Planner** | 基于 Confluence 页面的团队活动规划 Agent |
| **Q&A Creator** | 从 Confluence 内容生成问答 |
| **Weather Forecaster** | 调用外部 API 获取天气数据 |

### 2.3 自定义 DevOps 工具 AI 增强

通过 Forge 开发的自定义应用可实现：

- **PR 标题验证** — Bitbucket 自定义合并检查
- **Jira Issue 评论摘要** — 基于 OpenAI API（已有示例）
- **CI/CD 流水线编排** — Dynamic Pipelines
- **AWS CloudWatch + Compass 集成** — 监控告警与资产联动

---

## 三、Atlassian Opsgenie AI → Jira Service Management

### 3.1 产品合并公告

> **重要通知**：Atlassian 已宣布 **Opsgenie 将于 2027 年 4 月 5 日停服**，其告警和 on-call 功能已整合至 **Jira Service Management**。现有 Opsgenie 客户需在 2027 年前完成迁移。

### 3.2 AI 增强的告警管理（Jira Service Management）

Jira Service Management 中的 AI 能力：

| 功能 | 说明 |
|------|------|
| **AI 驱动的告警分组** | 智能聚合相似告警，减少告警风暴 |
| **事件响应自动化** | 基于历史数据学习，自动推荐响应策略 |
| **变更管理 AI** | 评估变更风险，建议审批流程 |
| **Problem Management** | 问题根因分析与关联知识推荐 |
| **根因分析 Agent** | Root Cause Analyzer（即将推出）— 自动关联部署/PR/提交/Jira 工单 |

### 3.3 PagerDuty 集成的 AI 能力

官方产品页面未单独列出 PagerDuty 的 AI 增强功能。但 Jira Service Management 支持与 PagerDuty 双向集成，AI 增强主要体现在：

- 告警聚合与优先级判断
- 响应工作流自动化
- 事件与 Jira 工单的自动关联

---

## 四、Jira Software AI

### 4.1 核心 AI 功能（Jira 内置 Rovo）

| 功能 | 说明 |
|------|------|
| **Work Breakdown** | AI 将复杂 Epic 一键拆解为子任务，含摘要和描述 |
| **Work Create** | Rovo 从 Confluence/Slack/邮件/图片等多渠道捕获工作 |
| **Related Resources** | 智能推荐相关的 Jira 工单和 Confluence 页面 |
| **工作项富化** | AI 自动补全上下文，减少信息孤岛 |
| **目标追踪** | AI 分析团队目标进展，识别风险 |

### 4.2 Sprint 规划的 AI 辅助

Jira AI 辅助 Sprint 规划的实践：

- **AI 辅助任务拆分** — 将高层级 Epic 通过自然语言拆分为可执行的 Story/Task
- **工作就绪度检查** — **Work Readiness Checker Agent** 确保工单描述清晰、验收标准明确，减少返工
- **自动化工作流** — **Workflow Builder Agent** 支持用自然语言构建 Jira 工作流

### 4.3 团队 Velocity 的 AI 预测

官方产品页面**未明确提及** velocity 预测功能。但 Jira 的 AI 驱动分析能力包括：

- **Cycle Time 分析** — 跟踪工程团队的 cycle time
- **Burndown/Burnup 图表** — AI 增强的团队绩效可视化
- **趋势识别** — Rovo AI 跨 Jira 工单识别主题趋势，辅助规划决策

---

## 五、Atlassian Rovo for DevOps

### 5.1 Rovo Agent 概览

Rovo Agents 是 Atlassian 的核心 AI Agent 产品，已在 Jira/Confluence/Jira Service Management 中深度集成。

**三大 Agent 类型：**

| Agent 类型 | 适用场景 |
|-----------|----------|
| **All Teams** | 通用任务（项目管理、知识整理、流程自动化） |
| **Service Teams** | IT 服务管理（自动化工单响应、Incident 响应、PIR 生成） |
| **Software Development Teams** | 研发效能（代码规划、代码生成、Code Review） |

### 5.2 Rovo Dev — 开发者专用 AI Agent

Rovo Dev 是 Atlassian 面向软件研发团队的 Agentic AI 产品。

**四大核心能力：**

| 能力 | 功能描述 |
|------|----------|
| **Code Planning** | 从 Jira story 生成代码计划，关联知识库和代码库 |
| **Code Generation** | 将 Jira work item 转化为代码（重构、测试、文档） |
| **Code Review** | AI 分析 PR，检查是否满足 Jira acceptance criteria，代码质量审查 |
| **Code Automation** | 多步骤工作流编排，并行执行多个 Agent 任务 |

**Rovo Dev 覆盖的开发环节：**

- **命令行（CLI）** — `Rovo Dev CLI` 集成到终端，理解代码仓库和 Jira 计划
- **IDE** — VS Code 插件，保持开发者 flow
- **Jira** — 从 Jira 工单启动 AI 会话，生成代码并创建 PR
- **Bitbucket Cloud** — PR 审查、构建失败排查、部署总结
- **GitHub** — PR 审查，确保 acceptance criteria 满足

**客户案例：**
- Statsig：PR cycle time 缩短
- Released：Code Reviewer Agent 补充传统 linting/测试
- OceanMD：PR 质量提升，减少噪音
- NEWWORK Software：自动化验收测试，节省数十小时

### 5.3 Rovo 与 Jira/Bitbucket 的集成

**Jira 集成深度：**
- Rovo Search 内嵌 Jira 上下文
- 从 Jira 工单直接启动 Rovo Chat
- Agent 可读取/创建/更新 Jira 工单
- Workflow Builder Agent 自然语言构建工作流

**Bitbucket 集成深度：**
- AI 辅助 PR 审查
- 构建失败时 AI 自动分析
- 部署总结自动生成
- Dynamic Pipelines 编排 CI/CD

### 5.4 Rovo Studio（低代码 Agent 构建）

Rovo Studio 是 2025 年 Team '25 发布的低代码 Agent 构建平台：

| 模块 | 功能 |
|------|------|
| **Agents** | 自定义 Agent，支持添加知识库/Actions/外部工具（含 MCP 服务器） |
| **Automation** | 自然语言创建自动化规则 |
| **Hubs** | 构建动态公司内网/帮助中心 |
| **Assets** | 将资源链接到 Jira Service Management |

---

## 六、Atlassian DevOps AI 战略整合

### 6.1 Teamwork Graph — AI 数据层

Teamwork Graph 是 Atlassian 的数据智能层，连接所有 Atlassian 和第三方 SaaS 数据：

- **100+ 开箱即用连接器**（Jira, Confluence, Bitbucket, GitHub, Gmail, Google Drive, Figma, Slack 等）
- **超 10 亿数据对象的连接**映射
- **统一数据 → 学习上下文 → 富化体验 → 持续演进**
- Rovo 的所有 AI 能力均构建于 Teamwork Graph 之上

### 6.2 五大产品线的 AI 协同

| 产品 | AI 角色 |
|------|---------|
| **Jira** | 项目管理 AI 化（Sprint 规划、任务拆分、进度追踪、Agentic Workflow） |
| **Confluence** | 知识管理 AI 化（智能搜索、内容生成、问答） |
| **Bitbucket** | 代码开发 AI 化（PR Review、CI/CD 编排、代码生成） |
| **Jira Service Management** | IT 运维 AI 化（告警聚合、Incident 响应、根因分析） |
| **Rovo** | 全平台 AI 助手（Search/Chat/Agents/Studio） |

### 6.3 AI 信任与合规

Atlassian 的 AI 平台遵循 **Responsible Technology Principles**：

- **透明性** — 用户可了解 AI 如何处理数据
- **信任** — 不使用客户数据训练第三方 LLM
- **权限感知** — AI 始终尊重现有权限体系
- **合规** — SOC 2、ISO 27001、FedRAMP 支持
- **数据 Residency** — 支持数据本地化存储（部分产品）

### 6.4 开发者扩展路径

```
自研/第三方 LLM (OpenAI/Anthropic)
         ↓
    Forge LLMs (EAP)
         ↓
    自定义 Agent 开发
         ↓
    Rovo Agent 模块发布到 Marketplace
```

**Rovo Dev 与 Forge 的关系：**
- Rovo Dev 是面向终端用户的 AI 产品
- Forge 是开发者构建自定义 Rovo Agent 的平台
- 两者共享 `rovo:agent` 和 `action` 模块体系

---

## 七、总结：Atlassian DevOps AI 能力矩阵

| 维度 | 核心产品 | AI 能力 | 集成深度 |
|------|----------|---------|---------|
| **研发计划** | Jira + Rovo | Sprint 规划辅助、任务拆分、AI Agentic Workflow | 深度集成 |
| **代码开发** | Bitbucket + Rovo Dev | 代码生成、PR 审查、CI/CD 编排 | 深度集成 |
| **告警运维** | Jira Service Management（原 Opsgenie） | 告警聚合、根因分析、Incident 响应 | 深度集成 |
| **知识管理** | Confluence + Rovo | 语义搜索、智能问答、内容生成 | 深度集成 |
| **AI 扩展平台** | Forge + Rovo Agent | 自定义 Agent、LLM 集成、Tool Use | 开发者层级 |
| **数据智能层** | Teamwork Graph | 跨产品数据统一、上下文学习 | 平台层级 |

**战略定位**：Atlassian 通过 Rovo 将 AI 能力普惠化（2025 年 Team '25 宣布向所有 Jira/Confluence/JSM 用户免费开放基础 Rovo 功能），通过 Forge 开放生态扩展，通过 Teamwork Graph 构建数据护城河，形成"平台 + 应用 + 生态"的三层 AI 架构。
