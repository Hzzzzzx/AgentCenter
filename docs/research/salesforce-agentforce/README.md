# Salesforce Agentforce 平台调研报告

**调研日期**：2026年3月25日  
**平台**：Salesforce Agentforce、Atlas Reasoning Engine、Data 360  
**网址**：https://www.salesforce.com/agentforce/

---

## 一、平台定位和核心功能

### 1.1 平台定位

Agentforce 定位为**"最完整的企业级 Agentic AI 平台"**，其核心价值主张是：

- **数字劳动力**：为企业提供 24/7 自主工作的 AI Agent
- **统一平台**：整合人类、应用、AI Agent 和数据
- **企业级信任**：内置安全护栏和合规控制

### 1.2 核心架构

```
┌─────────────────────────────────────────────────────┐
│                Agentforce 360 Platform               │
├─────────────────────────────────────────────────────┤
│  • Embed Anywhere（全渠道嵌入）                       │
│  • Supervise & Optimize（监督和优化）                 │
│  • Agents for Any Use Case（任意场景）               │
│  • Build & Test Fast（快速构建测试）                 │
│  • Deep Data Integration（深度数据集成）              │
└─────────────────────────────────────────────────────┘
```

### 1.3 Atlas Reasoning Engine（核心推理引擎）

Agentforce 的核心是 **Atlas Reasoning Engine**，它实现了：

1. **理解**：分析用户意图和问题范围
2. **决策**：确定需要的数据和行动
3. **执行**：自主完成任务

### 1.4 预置 Agent 类型

| Agent 类型 | 功能 | 业务价值 |
|-----------|------|---------|
| **Service Agent** | 客户服务支持 | 减少 30% 服务案例，88% 加速解决 |
| **Sales Development Rep** | 24/7 潜在客户开发 | 提高效率，缩短销售周期 |
| **Sales Coach** | 个性化销售培训 | 提升销售技能 |
| **Merchandiser** | 电商商品管理 | 简化日常运营 |
| **Campaign Optimizer** | 营销活动优化 | 自动化营销全流程 |
| **Employee Agent** | 员工支持 | 提高生产力 |

---

## 二、Einstein Copilot（Salesforce 内置 AI）

### 2.1 定位演变

**重要变化**：Einstein Copilot 已整合到 Agentforce 平台中，现在被称为 **Agentforce（员工辅助功能）**。

### 2.2 核心能力

Einstein 作为 Salesforce 的 AI 品牌，提供**三种 AI 能力**：

| AI 类型 | 推出时间 | 功能 |
|---------|---------|------|
| **预测 AI** | 2016 年 | 预测性线索评分、预测性预测、客户流失预测 |
| **生成 AI** | 2023 年 | AI 通话摘要、邮件草稿生成、内容创作 |
| **代理 AI** | 2024 年 | 自主规划和执行、多步骤任务处理、24/7 自主工作 |

### 2.3 Einstein Trust Layer

安全框架确保：
- **动态锚定**：基于真实业务数据生成回答
- **零数据保留**：数据不会用于训练外部模型
- **敏感数据掩码**：自动识别和保护 PII
- **毒性检测**：防止有害内容

---

## 三、Agentforce Studio（构建自定义 Agent）

### 3.1 Agentforce Builder

**定位**：低代码 + 专业代码双模式 AI Agent 构建器

### 3.2 核心特性

#### 3.2.1 对话式构建

```markdown
用户：我需要一个帮助客户管理订单的 Agent

Agentforce Assistant：
✅ 已创建"订单管理"主题
✅ 已添加"查询订单状态"操作
✅ 已添加"修改订单"操作
✅ 已设置护栏：仅处理已登录用户请求
```

#### 3.2.2 Agentforce Script（新功能）

**混合推理系统**：
- 确定性工作流（必须执行的逻辑）
- + LLM 推理（灵活处理）

```javascript
when(user_intent == "schedule_appointment") {
  required: [
    verify_customer_identity(),
    check_availability()
  ],
  flexible: [
    handle_special_requests(),
    suggest_alternatives()
  ]
}
```

### 3.3 三种视图

| 视图 | 适用场景 | 特点 |
|-----|---------|------|
| **Canvas 视图** | 业务用户 | 自然语言，文档式编辑 |
| **Low-Code Canvas** | 低代码开发者 | 可视化拖拽 |
| **Script 视图** | 专业开发者 | 代码编辑，语法高亮 |

---

## 四、Data 360 和 Flow 集成

### 4.1 Data 360（原 Data Cloud）

**核心价值**："让企业数据随时可用，无需移动数据"

### 4.2 Zero-Copy 集成

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Snowflake   │     │  Databricks  │     │  BigQuery    │
└──────┬───────┘     └──────┬───────┘     └──────┬───────┘
       │                    │                    │
       └────────────────────┼────────────────────┘
                            │
                    ┌───────▼────────┐
                    │   Zero-Copy    │
                    │   Integration  │
                    └───────┬────────┘
                            │
                    ┌───────▼────────┐
                    │    Data 360    │
                    └───────┬────────┘
                            │
                    ┌───────▼────────┐
                    │  Agentforce    │
                    └────────────────┘
```

### 4.3 支持的数据源

- **云数仓**：Snowflake, Databricks, Google BigQuery
- **云存储**：AWS S3, Google Cloud Storage
- **数据库**：Redshift, IBM DB2
- **第三方应用**：200+ 预置连接器

---

## 五、定价模型

### 5.1 三种计费模式

#### 模式 1: Flex Credits（推荐）

- **价格**：$500 / 100,000 Credits
- **计费单位**：每个 Action = 20 Credits

#### 模式 2: Conversations

- **价格**：$2 / 对话
- **特点**：固定价格，仅限客户面向 Agent

#### 模式 3: 按用户许可

- **Agentforce Add-ons**：$125/用户/月
- **Agentforce Industries**：$150/用户/月
- **Agentforce 1 Editions**：$550/用户/月起

### 5.2 免费层

**Salesforce Foundations**：
- Agent Builder
- Prompt Builder
- 200k Flex Credits
- 250k Data 360 Credits
- **完全免费**

---

## 六、与 AgentCenter 对比分析

### 6.1 对比维度

| 维度 | Salesforce Agentforce | AgentCenter | 优势方 |
|-----|---------------------|-------------|--------|
| **平台定位** | 企业级 SaaS 平台 | 开源/自托管 Agent 框架 | 各有优势 |
| **部署模式** | 云端托管 | 本地部署/云端 | AgentCenter |
| **数据集成** | Salesforce 生态深度集成 | 通用集成能力 | Agentforce |
| **开发门槛** | 低代码 + 专业代码 | 需要技术背景 | Agentforce |
| **成本结构** | 按使用量计费 | 一次性/订阅制 | AgentCenter |
| **定制能力** | 受限于 Salesforce 生态 | 完全开放 | AgentCenter |
| **供应商锁定** | 高度依赖 Salesforce | 无锁定 | AgentCenter |

### 6.2 Salesforce Agentforce 的优势

| 优势 | 说明 |
|------|------|
| ✅ **生态系统整合** | 与 Salesforce CRM、Sales Cloud、Service Cloud 无缝集成 |
| ✅ **企业级信任** | Einstein Trust Layer 内置安全，SOCR 2/ISO 27001 认证 |
| ✅ **低代码开发** | 自然语言构建 Agent，AI 辅助开发 |
| ✅ **Data 360** | 统一客户视图，Zero-Copy 数据集成 |
| ✅ **成熟度** | Salesforce 十年 AI 创新，大量企业客户案例 |

### 6.3 潜在劣势

| 劣势 | 说明 |
|------|------|
| ❌ **供应商锁定** | 深度绑定 Salesforce 生态，迁移成本高 |
| ❌ **成本结构** | Flex Credits 按使用量计费，成本可能快速累积 |
| ❌ **定制限制** | 受限于 Salesforce 平台能力 |
| ❌ **数据主权** | 数据存储在 Salesforce 云端 |

---

## 七、客户案例和成果

| 公司 | 成果 | 场景 |
|-----|------|------|
| **OpenTable** | 30% 服务案例转移 | 餐厅预订管理 |
| **Finnair** | 88% 加速解决时间 | 客户服务 |
| **FedEx** | 2000%+ ROI | 客户数据激活 |
| **SharkNinja** | 24/7 客户支持 | 产品咨询 |

**G2 评价**：4.5/5 星，#1 Agentic AI 平台

---

## 八、总结

### 8.1 Agentforce 的核心价值

Salesforce Agentforce 是一个**成熟、安全、易用**的企业级 AI Agent 平台，特别适合：
- ✅ Salesforce 现有客户
- ✅ 需要快速部署的场景
- ✅ 企业级安全和合规要求
- ✅ 业务主导的 AI 项目

### 8.2 潜在挑战

- ❌ 供应商锁定风险
- ❌ 按使用量计费的不可预测性
- ❌ 定制能力受限于平台
- ❌ 数据主权问题

### 8.3 与 AgentCenter 的关系

Agentforce 和 AgentCenter 是**互补而非直接竞争**的关系：

| 平台 | 定位 | 目标用户 |
|------|------|---------|
| **Agentforce** | 企业级 SaaS，Salesforce 生态 | Salesforce 客户 |
| **AgentCenter** | 灵活框架，开放生态 | 技术团队、非 Salesforce 客户 |

---

## 九、参考资源

- [Agentforce 官网](https://www.salesforce.com/agentforce/)
- [Agentforce Builder](https://www.salesforce.com/agentforce/agent-builder/)
- [Agentforce 定价](https://www.salesforce.com/agentforce/pricing/)
- [Data 360](https://www.salesforce.com/data/)
- [G2 评价](https://www.g2.com/categories/ai-agent-builders)

---

**报告日期**：2026年3月25日  
**版本**：v1.0
