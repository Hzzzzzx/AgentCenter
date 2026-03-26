# ServiceNow AI Agent 战略研究报告：DevOps 与 IT 运维

**研究日期：2026年3月26日**
**覆盖领域：AI Agent、ITSM、AIOps、DevOps 集成、平台工程**

---

## 一、ServiceNow AI Agent 概览

ServiceNow 将自身定位为"**AI 平台 for Business Transformation**"，其 AI Agent 战略的核心逻辑是：**在单一平台上整合 AI、数据与工作流**，让 AI Agent 成为企业自主劳动力的核心组成。

### 核心产品线
| 产品 | 定位 |
|------|------|
| **AI Agents** | 自主执行复杂业务任务的 AI Agent |
| **Now Assist** | GenAI 能力（被融入更广泛的 AI Agents 体系） |
| **AI Control Tower** | 企业级 AI 治理与可视化管理 |
| **IT Operations Management (ITOM)** | AIOps 智能运维 |
| **Service Operations Workspace** | 统一运维工作空间 |
| **Integration Hub** | 跨系统集成自动化 |

**平台版本演进**：Washington D.C. → Xanadu → Yokohama（最新，2025年3月发布）

---

## 二、AI Agent 核心技术架构

根据 Leviathor 的深度分析，ServiceNow AI Agent 依托三大核心技术：

### 2.1 自然语言理解（NLU）
- **意图识别**：准确理解用户请求目标
- **实体提取**：识别日期、名称、问题类型等关键信息
- **情感分析**：判断用户情绪以调整响应策略
- **上下文感知**：跨多轮对话维持理解

### 2.2 机器学习预测分析（ML）
- **预测性事件管理**：预测潜在 IT 故障
- **根因分析**：识别重复问题的根本原因
- **工作负载预测**：优化支持团队资源分配
- **知识推荐**：向用户推荐相关解决方案

### 2.3 机器人流程自动化（RPA）集成
- **自动化工单创建**：基于用户输入生成服务单
- **系统配置**：自动化用户账户设置或软件安装
- **数据录入与更新**：执行常规数据管理任务
- **流程编排**：协调多步骤自动化工作流

---

## 三、重点产品能力详解

### 3.1 AI Agents for IT Service Management（ITSM）

根据 Gartner 报告和 Business Wire 文章，ServiceNow 在 2024年10月被评为 **Gartner Magic Quadrant for AI Applications in ITSM 领导者**。

**Now Assist for ITSM 核心功能**（2023年9月 Vancouver 版本推出）：
- **AI Search**：基于 Now LLM GenAI 模型回答问题，生成可操作的知识摘要
- **Now Assist in Virtual Agent**：使用 LLM 创建自然语言对话体验，提升自助服务水平
- **聊天摘要**：总结用户与虚拟 Agent 或在线 Agent 之间的对话，实现更快交接
- **Write with Now Assist**：使用 GenAI 帮助 Agent 创建和编辑聊天回复和电子邮件
- **事件摘要**：生成事件的综合摘要、已采取行动和推荐后续步骤，加速解决响应时间
- **解决方案笔记生成**：根据事件笔记和活动自动生成详细解决方案笔记
- **知识文章生成**：自动为人工审核生成和发布知识文章
- **变更请求摘要**：捕获变更请求的关键细节，包括状态、风险和实施计划

**Yokohama 版本新增 AI Agent（2025年3月）**：
- **安全运营 AI Agent**：转变安全运营，简化整个事件生命周期，消除重复性任务
- **自主变更管理 AI Agent**：作为资深变更经理，分析影响、历史数据和类似变更，生成自定义实施、测试和回退计划
- **主动网络测试与修复 AI Agent**：作为 AI 故障排除器，在影响性能前自动检测、诊断和解决网络问题

### 3.2 IT Operations Management（AIOps）

**ITOM 的 AI 能力**：
- **智能告警压缩与分类**：减少告警噪音，聚焦关键事件
- **服务健康洞察**：跨基础设施和应用的统一可见性
- **云成本优化**：优化云交付和支出
- **预测性运营**：在问题发生前主动识别和解决

**Service Operations Workspace**：
- 从单一工作空间**预测、预防和解决**事件
- 集成 AI 驱动的问题检测和诊断能力

### 3.3 AI Control Tower

作为企业级 AI 治理解决方案：
- **策略连接**：将 AI 战略与业务目标对齐
- **治理管理**：跨企业统一管理 AI Agent 生命周期
- **性能监控**：实时追踪 AI Agent 表现和业务 KPI

### 3.4 Integration Hub

- **降低集成成本和复杂性**
- 支持与 Jira、GitHub、Azure DevOps 等 DevOps 工具的连接
- 提供预构建的连接器和 API 管理能力

---

## 四、平台工程与开发者能力

### 4.1 Creator Studio
- 扩展低代码/无代码应用开发能力
- 支持服务目录项生成、应用生成和剧本生成

### 4.2 AI Agent Orchestrator & AI Agent Studio（Yokohama 版本GA）
- **AI Agent Studio**：通过自然语言描述引导式设置，简化新 Agent 设计配置
- **性能管理仪表板**：可视化 AI Agent 使用、质量和价值，AI Agent 工作流与业务 KPI 绑定

### 4.3 自动化引擎增强
- 提供所有自动化环境的统一视图
- 简化 RPA 部署

---

## 五、DevOps 工具集成

### 5.1 已知集成方向
- **Jira**：双向工单同步，事件与项目联动
- **GitHub**：代码提交与变更/事件管理关联
- **Azure DevOps**：工作项和构建集成
- **Microsoft 365 & Slack**：Now Assist 集成，支持对话式 AI

### 5.2 数据层集成
- **Workflow Data Fabric**：跨组织数据集成，不管数据所在系统
- **Knowledge Graph & Common Service Data Model (CSDM)**：标准化框架，管理 IT 和业务服务，统一数百个技术类别、系统

---

## 六、市场定位与竞争分析

根据 Gartner 报告：
> "I&O 负责人面临支持成本上升和员工参与度/生产力下降的挑战。AI 能力使 I&O 团队能够通过洞察和自动化优化 IT 支持和服务管理流程（如事件和问题管理）。这可以带来劳动力节省等成本的实际减少（通过自动处理支持问题和请求）、更快的问题解决速度，以及分诊、分类和专家识别准确性的提高。"

**与独立 AI 聊天机器人的差异**：
| 维度 | ServiceNow AI Agents | 独立聊天机器人 |
|------|---------------------|----------------|
| 平台集成 | 与 ITSM、ITOM、CSM 深度集成 | 需通过 API 集成 |
| 自动化范围 | 跨 ITSM/ITOM 复杂工作流 | 仅对话界面 |
| 数据利用 | 利用 ServiceNow 历史数据训练 ML | 数据隔离 |
| 部署复杂度 | 初始复杂但统一管理 | 快速部署但深度有限 |

---

## 七、关键发布节点

| 时间 | 版本 | 重要更新 |
|------|------|----------|
| 2023年9月 | Vancouver | Now Assist for ITSM 首发，GenAI 能力正式落地 |
| 2024年3月 | Washington D.C. | Now Platform 重大更新 |
| 2024年5月 | Knowledge 2024 | 新增 SPM、App 生成、剧本生成；RaptorDB 发布 |
| 2024年9月 | Xanadu | 数百个新 AI 能力；ServiceNow AI Agents 正式发布 |
| 2024年10月 | — | Gartner Magic Quadrant for AI Applications in ITSM 领导者 |
| 2025年3月 | Yokohama | 预配置 AI Agent 团队；AI Agent Orchestrator/Studio GA；CSDM 增强 |

---

## 八、总结

ServiceNow 的 AI Agent 战略核心是**将 AI 深度嵌入 ITSM/ITOM 工作流**，而非仅提供独立的 AI 工具。其优势在于：

1. **平台整合**：单一数据平台 + AI + 工作流，无缝连接
2. **企业级治理**：AI Control Tower 提供全企业 AI 可视性和控制
3. **自主 Agent 能力**：从单问题解决到完整事件响应工作流
4. **开发者友好**：AI Agent Studio 支持自然语言配置，降低使用门槛

**局限性**：
- ServiceNow 官方产品页面均为 JS 动态渲染，公开产品细节获取困难
- DevOps 工具集成（GitHub/Jira/Azure DevOps）的具体技术细节披露有限
- 部分 URL 已失效或重组，信息可能存在时效性

---

*本报告基于 ITPro Today、Leviathor、Business Wire/GuruFocus、Medium 等第三方公开资料编译，部分产品细节需以 ServiceNow 官方最新文档为准。*
