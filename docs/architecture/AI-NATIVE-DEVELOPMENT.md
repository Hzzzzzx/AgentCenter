# AI 原生开发流程

> 生成时间：2026-04-02
> 状态：设计讨论中
> 关联文档：[架构总览](./ARCHITECTURE-OVERVIEW.md) | [验证体系](./VERIFICATION-FRAMEWORK.md)

---

## 一、核心洞察

```
当"做出来"的成本低于"想清楚"的成本时，，
流程顺序就会反转。
```

### 传统 vs AI 原生

| 维度 | 传统流程 | AI 原生流程 |
|------|----------|--------------|
| 起始 | 需求文档 (精确, 前置) | 意图 (模糊, 对话式) |
| 方式 | 先想清楚再做出来 | 先做出来再看对不对 |
| 成本 | 原型成本极高 | 原型成本趋近于零 |
| 需求形式 | PRD 文档 (一次性) | 对话过程 (逐步精确化) |
| 验证时机 | 开发完成后 | 看到原型后立即验证 |
| 迭代 | 改一次代价极大 | 改一次代价极低 |

---

## 二、AI 原生开发 7 步流程

```
  ① 意图表达 ──▶ ② 儿型生成 ──▶ ③ 人工评审 ──▶ ④ 迭代优化 ──▶ ⑤ 规则锁定 ──▶ ⑥ 自动验证 ──▶ ⑦ 交付上线
```

### Step 1: 意图表达

```
👤 "我需要一个用户登录功能，支持手机号+验证码登录"
```

**特点**：
- 不需要 PRD，不需要设计文档
- 用自然语言描述意图即可
- 系统基于领域知识主动提问澄清

- 用户只需要做选择题 + 补充特殊情况

### Step 2: 原型生成

```
🤖 理解意图，生成可运行的高保真原型:
   ├── 前端: 登录页 UI (可直接在浏览器预览)
   ├── 后端: 验证码发送/校验 API (可直接调用)
   └── 交互: 输入手机号 → 发送验证码 → 输入验证码 → 登录成功
```

**系统自动执行**:
- Layer 1: 编译/构建检查
- Layer 2: 静态分析/安全扫描
- 通过后进入用户评审

### Step 3: 人工评审

```
👤 "验证码输入框应该支持粘贴，登录失败要有明确提示，
   还需要加一个'获取验证码'的倒计时"
```

**特点**：
- 人在体验原型后给出反馈
- 比写 PRD 容易得多
- 系统自动展示原型预览

### Step 4: 迭代优化

```
🤖 根据反馈修改原型
👤 继续评审... 不满意继续提
🤖 继续修改...
👤 "这次差不多了"
```

**特点**：
- 多轮迭代直到满意 (通常 2-5 轮)
- 每轮都生成新的可运行原型

### Step 5: 规则锁定

```
🤖 从对话过程中提取验证规则:
   ├── 验证码 6 位数字
   ├── 60 秒倒计时期间不可重发
   ├── 验证码 5 分钟过期
   ├── 同一手机号每天最多发送 10 次
   ├── 登录失败显示"手机号或验证码错误"
   ├── 连续失败 5 次锁定 15 分钟
   └── 登录成功后跳转到首页

用户确认: "这些规则对吗？" → 可以增删改
```

**关键洞察**：
- 规则从对话中"涌现"，而非事前写定
- 用户只需要确认/微调，不需要从零编写

### Step 6: 自动验证

```
🤖 基于锁定的规则自动生成:
   ├── 单元测试 (验证码生成/校验/过期逻辑)
   ├── 集成测试 (完整登录流程)
   ├── 边界测试 (频率限制/锁定机制)
   ├── 安全测试 (暴力破解防护)
   └── E2E 测试 (用户操作流程)

自动运行全部验证 → 全部通过 → 进入交付
```

### Step 7: 交付上线

```
🤖 自动完成:
   ├── 代码提交到仓库
   ├── 构建/打包
   ├── 部署到开发环境 → 测试环境 → 预生产 → 生产
   ├── 监控就绪
   └── 通知相关人员
```

---

## 三、新增对象模型

在原有 8 个对象基础上，AI 原生流程需要新增 3 个对象：

### 3.1 Intent (意图)

```yaml
Intent:
  id: String!
  description: String              # 用户的自然语言描述
  dialogHistory: [Message]      # 多轮对话历史
  extractedRequirements: [StructuredRequirement]  # 从对话中提取的结构化需求
  status: Enum                   # DRAFT | REFINING | CONFIRMED | IMPLEMENTING | COMPLETED
  linkedPrototypes: [Prototype]
  linkedValidationRules: [ValidationRule]
  createdAt: DateTime
  confirmedAt: DateTime?
```

### 3.2 Prototype (原型)

```yaml
Prototype:
  id: String!
  intent: Intent
  version: Int                    # 迭代版本号
  codeArtifacts: [CodeArtifact]    # AI 生成的代码
  previewUrl: String?              # 预览地址
  status: Enum                    # GENERATED | UNDER_REVIEW | ACCEPTED | REJECTED
  reviewFeedback: [Feedback]       # 评审反馈
  generatedAt: DateTime
```

### 3.3 ValidationRule (验证规则)

```yaml
ValidationRule:
  id: String!
  intent: Intent
  prototype: Prototype
  category: Enum               # BUSINESS_RULE | INTERACTION | DATA_CONSTRAINT | BOUNDARY | NON_FUNCTIONAL
  description: String           # 规则描述
  source: Enum                   # EXTRACTED_FROM_DIALOG | INHERITED_FROM_KNOWLEDGE_BASE | USER_SUPPLEMENT
  assertion: String              # 可执行的断言表达式
  testCases: [TestCase]          # 自动生成的测试用例
  status: Enum                   # DRAFT | CONFIRMED | ACTIVE
```

### 关系图

```
Intent ──1:N──▶ Prototype ──1:N──▶ ValidationRule
   │                   │                      │
   │    迭代过程中持续精确化          │
   └────────────────────┴──────────────────────┘

  当 Prototype 被确认 且 ValidationRule 全部通过后:
  Intent + Prototype + ValidationRule → 正式进入 DevOps 流水线
                           (复用原有 WorkItem → CodeChange → Artifact → Deployment 流程)
```

---

## 四、人的角色变化

| 维度 | 传统开发 | AI 原生开发 |
|------|----------|--------------|
| 核心工作 | 写代码 | 做判断 |
| 需求形式 | PRD 文档 | 对话 |
| 验证方式 | 写测试用例 | 确认 AI 提取的规则 |
| 迭代成本 | 改一次代价极高 | 改一次代价极低 |
| 价值体现 | 实现能力 | 判断力 + 领域知识 |

**核心结论**：人的价值从"写代码"变成了"做判断"。
