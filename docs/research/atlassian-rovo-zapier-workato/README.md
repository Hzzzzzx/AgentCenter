# Atlassian Rovo、Zapier AI 与 Workato AI 调研报告

**调研日期**：2026年3月25日  
**平台**：Atlassian Rovo、Zapier AI、Workato AI  

---

# 一、Atlassian Rovo 深度分析

## 1.1 产品定位

**Atlassian Rovo** 是 Atlassian 推出的企业级 AI 助手平台，核心定位是"**AI that knows your business**"——一个能够理解企业上下文的 AI 系统。

**核心价值主张**：
- 打破知识孤岛，连接企业所有数据源
- 基于企业上下文提供智能回答和行动
- 深度集成 Atlassian 生态（Jira、Confluence 等）

## 1.2 四大核心功能模块

| 模块 | 功能描述 | 关键特性 |
|------|----------|----------|
| **Rovo Search** | 跨应用企业搜索 | 跨 100+ SaaS 应用搜索，Teamwork Graph 驱动 |
| **Rovo Chat** | AI 对话助手 | 创建 Jira tickets、发送 Slack 消息 |
| **Rovo Agents** | 专业 AI 代理 | 服务团队 Agent、软件开发 Agent、通用团队 Agent |
| **Rovo Studio** | AI 构建平台 | 无代码/低代码构建 Agent |

## 1.3 Teamwork Graph（知识图谱）

**Teamwork Graph** 是 Rovo 的数据智能层，通过四个步骤实现知识管理：

1. **Unifying** - 统一 Atlassian 和 100+ 应用数据
2. **Learning** - 学习用户上下文（谁、做什么、怎么做）
3. **Enriching** - 智能增强应用体验
4. **Growing** - 随着使用持续优化

## 1.4 与 Jira/Confluence 集成

| 集成点 | 功能 |
|--------|------|
| **Jira** | Issue Organizer Agent，实时项目追踪，自动化 ticket 处理 |
| **Confluence** | 知识卡片、智能搜索、移动端 Chat |
| **跨产品** | 在 Jira 中显示 Confluence 数据，统一上下文 |

## 1.5 企业级功能

- **数据隔离与加密**：客户数据和学习结果隔离，每小时轮换密钥
- **运行时认证**：基于 OAuth 2.0 的实时用户授权
- **RBAC 权限控制**：细粒度角色访问控制
- **审计日志**：完整的操作可追溯性

---

# 二、Zapier AI 分析

## 2.1 产品定位

**Zapier** 是领先的 iPaaS（集成平台即服务），AI 增强后成为"**AI orchestration platform**"——连接 8,000+ 应用的 AI 编排平台。

## 2.2 AI 产品矩阵

| 产品 | 功能 | 适用场景 |
|------|------|----------|
| **Zapier Agents** | 可定制的 AI 代理 | 会议准备、线索筛选、内容创作 |
| **Zapier Copilot** | AI 自动化助手 | 用自然语言构建工作流 |
| **Zapier Chatbots** | AI 聊天机器人 | 客户问答、自定义外观和行为 |
| **Zapier Canvas** | 工作流可视化规划 | AI 辅助设计和映射工作流 |
| **Zapier MCP (Beta)** | MCP 协议支持 | 让外部 AI 工具执行 30,000+ 操作 |
| **Custom Actions** | 自定义 API 动作 | 自动生成 API 调用代码 |

## 2.3 Zapier Agents 核心能力

- **知识注入**：添加公司知识库让 Agent 理解业务上下文
- **8,000+ 应用连接**：执行跨应用操作
- **主动执行**：后台运行，无需持续监督
- **模板库**：预构建的 Agent 模板

---

# 三、Workato AI 分析

## 3.1 产品定位

**Workato** 是企业级 iPaaS 平台，AI 增强后提供"**Agentic Orchestration**"——企业级多 Agent 编排能力。其 AI Agent 称为"**Genies**"。

## 3.2 Workato Genies 预构建 Agent

| 部门 | Genie 类型 | 核心功能 |
|------|-----------|----------|
| **IT** | IT Genie | IT 运维自动化 |
| **Sales** | Sales Genie | 销售流程自动化 |
| **HR** | HR & Recruiting Genie | 招聘和入职自动化 |
| **Support** | Support Genie | 客户支持自动化 |
| **CX** | CX Genie | 客户体验管理 |
| **Marketing** | Marketing Genie | 营销自动化 |

## 3.3 Agent Studio 核心能力

- **低代码构建**：使用 750K+ 可复用 Recipes 和 Skills
- **Deep Action™**：执行完整业务流程（不只是对话）
- **企业级安全**：专利的运行时用户授权、完整审计
- **MCP Gateway**：完全托管的 MCP 服务器
- **1,200+ 应用连接**：企业级应用集成

---

# 四、平台对比分析

## 4.1 核心定位对比

| 维度 | Atlassian Rovo | Zapier AI | Workato AI |
|------|---------------|-----------|------------|
| **核心定位** | 企业知识 AI（垂直整合） | AI 编排平台（水平连接） | 企业级 Agent 编排（深度集成） |
| **目标用户** | Atlassian 生态用户 | SMB 到 Enterprise | 大型企业 |
| **核心优势** | Teamwork Graph 深度上下文 | 8,000+ 应用广度 | 企业级治理和深度集成 |
| **知识管理** | 原生 Teamwork Graph | 外部知识注入 | Enterprise Search |

## 4.2 技术架构对比

| 维度 | Rovo | Zapier | Workato |
|------|------|--------|---------|
| **Agent 构建** | Rovo Studio（无代码） | Agents 平台 | Agent Studio（低代码） |
| **集成数量** | 100+ 连接器 | 8,000+ 应用 | 1,200+ 应用 |
| **MCP 支持** | 支持 MCP 服务器 | Zapier MCP（Beta） | Enterprise MCP Gateway |
| **知识图谱** | Teamwork Graph（原生） | 无原生 | Enterprise Search |

---

# 五、与 AgentCenter 对比分析

## 5.1 优势分析

**相对于 Rovo**：

| AgentCenter 潜在优势 | Rovo 劣势 |
|---------------------|-----------|
| 更灵活的 Agent 定义 | 受限于 Atlassian 生态 |
| 跨平台知识管理 | 仅 Teamwork Graph |
| 开放式架构 | 闭源集成 |

**相对于 Zapier**：

| AgentCenter 潜在优势 | Zapier 劣势 |
|---------------------|-------------|
| 深度业务理解 | 偏浅层连接 |
| 原生知识管理 | 需外部注入知识 |
| 企业级治理 | SMB 起源，企业功能后加 |

**相对于 Workato**：

| AgentCenter 潜在优势 | Workato 劣势 |
|---------------------|-------------|
| 更低的学习曲线 | 复杂度高 |
| 更灵活的定价 | 企业定价昂贵 |
| 更快的部署速度 | 企业级复杂度 |

## 5.2 差异化机会

| 方向 | 机会描述 |
|------|----------|
| **AI-Native 设计** | 从零设计 Agent 优先架构，而非传统 iPaaS 加 AI |
| **混合知识管理** | 结合结构化知识图谱 + 非结构化 AI 理解 |
| **开发者友好** | 提供更强的自定义能力，平衡低代码和代码能力 |
| **垂直场景深耕** | 选择特定行业/场景做深度优化 |
| **成本优势** | 提供更灵活的定价模式 |
| **开源/开放生态** | 开放 Agent 定义标准，允许社区贡献 |

---

# 六、参考资源

- [Atlassian Rovo 官方页面](https://www.atlassian.com/software/rovo)
- [Rovo Features 文档](https://www.atlassian.com/software/rovo/features)
- [Teamwork Graph 介绍](https://www.atlassian.com/platform/teamwork-graph)
- [Zapier AI 产品页面](https://zapier.com/ai)
- [Zapier Agents 文档](https://zapier.com/agents)
- [Workato AI 产品页面](https://www.workato.com/ai)
- [Workato Agent Studio](https://www.workato.com/agentstudio)

---

**报告生成时间**：2026年3月25日
