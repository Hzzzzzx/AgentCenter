# AgentCenter 文档中心

> 企业智能中枢 — 通过对话编排企业内部全套工具流程

---

## 文档索引

### 产品定位

| 文档 | 说明 | 状态 |
|------|------|------|
| [PRODUCT-VISION.md](./PRODUCT-VISION.md) | 产品畅想报告 | 活跃 |
| [Azure-DevOps-深度调研报告.md](./Azure-DevOps-深度调研报告.md) | Azure DevOps 平台调研 | 活跃 |

### 架构设计 (`architecture/`)

| 文档 | 说明 | 状态 |
|------|------|------|
| [README.md](./architecture/README.md) | 架构文档入口、4+1 视图边界和阅读顺序 | 活跃 |
| [DISCUSSION-HANDOFF-2026-04-28.md](./architecture/DISCUSSION-HANDOFF-2026-04-28.md) | 当前讨论进展交接，供后续 Agent 会话继续讨论 | 活跃 |
| [APPLICATION-ARCHITECTURE-BASELINE.md](./architecture/APPLICATION-ARCHITECTURE-BASELINE.md) | 企业级应用架构基线：统一上下文、多项目多用户、性能并发、安全治理 | 活跃 |
| [ARCHITECTURE-OVERVIEW.md](./architecture/ARCHITECTURE-OVERVIEW.md) | 4+1 视图架构总览 | 活跃 |
| [UNIFIED-DOMAIN-MODEL.md](./architecture/UNIFIED-DOMAIN-MODEL.md) | 统一对象模型 (8+3 对象) | 活跃 |
| [AGENT-RUNTIME-BRIDGE-DEVELOPMENT-BLUEPRINT.md](./architecture/AGENT-RUNTIME-BRIDGE-DEVELOPMENT-BLUEPRINT.md) | Java Bridge、Vue 工作台、OpenCode Runtime Adapter 开发蓝图 | 活跃 |
| [ADR-001-OPENCODE-BRIDGE-SSE-REST.md](./architecture/ADR-001-OPENCODE-BRIDGE-SSE-REST.md) | M1 实施决策：REST+SSE 方案 | 活跃 |
| [AGENT-RUNTIME-BRIDGE-M1-RUNBOOK.md](./architecture/AGENT-RUNTIME-BRIDGE-M1-RUNBOOK.md) | M1 快速启动指南：环境准备、工作目录配置、启动步骤 | 活跃 |
| [REFERENCE-PROJECTS-AND-RESEARCH.md](./architecture/REFERENCE-PROJECTS-AND-RESEARCH.md) | 开源框架、竞品调研和可引入经验登记 | 活跃 |
| [ENVIRONMENT-AND-PROMOTION.md](./architecture/ENVIRONMENT-AND-PROMOTION.md) | 多环境隔离与晋升机制 | 活跃 |
| [INTEGRATION-ROADMAP.md](./architecture/INTEGRATION-ROADMAP.md) | 存量系统逐步整合路线 | 活跃 |
| [AI-NATIVE-DEVELOPMENT.md](./architecture/AI-NATIVE-DEVELOPMENT.md) | AI 原生开发流程 | 活跃 |
| [VERIFICATION-FRAMEWORK.md](./architecture/VERIFICATION-FRAMEWORK.md) | 验证体系与可视化 | 活跃 |

### 原型与高保真 (`prototype/`)

| 文档 | 说明 | 状态 |
|------|------|------|
| [prototype/README.md](./prototype/README.md) | 当前首页高保真基线和归档入口 | 活跃 |
| [prototype/homepage.html](./prototype/homepage.html) | 当前静态首页高保真原型 | 活跃 |
| [prototype/archive/homepage-workbench-2026-04-27/](./prototype/archive/homepage-workbench-2026-04-27/README.md) | 工作台基线归档（静态原型 + React Demo 快照） | 归档 |
| [prototype/archive/client-demo-2026-04-29/](./prototype/archive/client-demo-2026-04-29/) | 早期首页视觉探索草稿（a-version 系列） | 归档 |

### 归档 (`archive/`)

| 文件 | 说明 |
|------|------|
| [archive/root-loose-files/](./archive/root-loose-files/) | 项目根目录散落文件（旧截图、参考图片、PPTX） |

### 行业调研 (`research/`)

#### 综合性调研

| 文档 | 说明 | 状态 |
|------|------|------|
| [enterprise-ai-summary/README.md](./research/enterprise-ai-summary/README.md) | 企业级 AI Agent 平台综合调研 | 活跃 |
| [devops-ai-platforms/devops-ai-agent-research-2026-03-26.md](./research/devops-ai-platforms/devops-ai-agent-research-2026-03-26.md) | DevOps AI Agent 综合调研 | 活跃 |
| [agentic-workbench-reference-patterns/README.md](./research/agentic-workbench-reference-patterns/README.md) | Agentic Workbench 开源参考项目与可迁移经验 | 活跃 |

#### 平台专项调研 (`research/`)

| 平台 | 目录 | 说明 |
|------|------|------|
| Microsoft/GitHub | [microsoft-github-devops-ai/](./research/microsoft-github-devops-ai/README.md) | Copilot + Azure DevOps AI |
| GitLab | [gitlab-devops-ai/](./research/gitlab-devops-ai/README.md) | GitLab Duo AI |
| Atlassian | [atlassian-devops-ai/](./research/atlassian-devops-ai/README.md) | Rovo + Teamwork Graph |
| ServiceNow | [servicenow-devops-ai/](./research/servicenow-devops-ai/README.md) | ServiceNow AI |
| IBM | [ibm-devops-ai/](./research/ibm-devops-ai/README.md) | watsonx + OpenShift |
| 国内云厂商 | [devops-ai-platforms/china-cloud-devops-ai/](./research/devops-ai-platforms/china-cloud-devops-ai/README.md) | 阿里云/腾讯云/华为云 |
| 钉钉/阿里 | [aliyun-dingtalk-ai/](./research/aliyun-dingtalk-ai/README.md) | 钉钉 AI + 百炼 |
| 百度/华为/腾讯 | [baidu-huawei-tencent-ai/](./research/baidu-huawei-tencent-ai/README.md) | 百度/华为/腾讯 AI |
| Coze | [coze-platform/](./research/coze-platform/README.md) | 字节 Coze 平台 |
| Salesforce | [salesforce-agentforce/](./research/salesforce-agentforce/README.md) | AgentForce |
| ServiceNow AI | [servicenow-ai/](./research/servicenow-ai/README.md) | ServiceNow AI 专项 |
| IBM Watsonx | [ibm-watsonx/](./research/ibm-watsonx/README.md) | IBM Watsonx 专项 |
| Microsoft Copilot | [microsoft-copilot/](./research/microsoft-copilot/README.md) | Copilot 全家桶 |
| Zapier/Workato | [atlassian-rovo-zapier-workato/](./research/atlassian-rovo-zapier-workato/README.md) | 自动化平台 AI |

---

## 文档统计

| 类别 | 数量 |
|------|------|
| 架构文档 | 11 |
| 原型与高保真 | 1 |
| 综合调研 | 3 |
| 平台专项调研 | 13 |
| **总计** | **28** |

---

*最后更新：2026-05-05*
