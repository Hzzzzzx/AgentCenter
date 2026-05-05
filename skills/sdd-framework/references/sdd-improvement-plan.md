# TianYuan SDD 开发体系改进方案 v3

> 版本：v3（经 Oracle + 深度双模型审查修订）  
> 创建日期：2026-05-03  
> 基于：六大 SDD 项目横向对比 + 双模型审查反馈  
> 原则：分场景不搞一刀切 · 保留核心优势 · 补齐资产维护 · 关键环节人工签字

---

## 一、当前诊断

| 优势（保持） | 劣势（改进） |
|-------------|-------------|
| Blueprint Graph — 唯一自动契约验证 | 全流程一刀切，typofix 也要走 Gates |
| UI Control Plane — 唯一 UI 层验证 | Agent 执行无行为约束 |
| 任务拆解可靠性 — Contract 保证不遗漏 | 文档信息密度低，人审不动 |
| Design Sync 三层防线 — 决策不丢失 | 无可视化输出 |
| Final Wave 四维验收 | 无 Token 成本控制 |
| | **资产维护空白** — 大量文档产出无生命周期管理 |

---

## 二、场景化全流程矩阵

### 2.1 五级场景定义 + 两种特殊协议

| 级别 | 场景 | 典型特征 | 分级依据 | 一个例子 |
|:---:|------|---------|---------|---------|
| **L0** | 琐碎修改 | 1 文件，< 5 行，无逻辑/合约变化 | **人标注** | typo，注释修正 |
| **L1** | 小 bug 修复 | 1-3 文件，根因明确，无新逻辑 | **人标注** | 空指针，参数传错 |
| **L2** | 小功能 | 1-5 文件，需求清晰，无跨层影响 | **人标注 + Agent 确认** | 加一个按钮，加一个 API 参数 |
| **L3** | 中型功能 | 3-10 文件，有 1-2 个设计选择 | **人标注 + Plan Agent 确认** | 重构一个模块，加新面板 |
| **L4** | 大型功能 | 8+ 文件，新模块或跨系统改动 | **人标注 + Plan Agent 确认** | 加认证系统，加新路由 |

| 特殊协议 | 触发条件 | 行为 |
|---------|---------|------|
| **🔄 重构模式** | 行为不变，结构变。适用于 L1-L4 任意级别 | 执行 L1-L4 流程 + Plan 必须声明"Expected contract changes"（哪些节点改名/删除/新增），Blueprint diff 只对声明外的意外变化报警 |
| **🚨 紧急协议** | `production_down | data_loss | security_breach` | 跳过所有仪式感，只保留安全扫描（不阻塞）+ VERIFY。修复后 24h 内必须补完审查卡片 + 回归测试 + post-mortem。补记项进入 `.sisyphus/pending-L6.md` 队列，每次新会话自动提醒 |

> **关键改进**（v2→v3）：重构和紧急不再是等级，而是叠加在等级上的协议/模式。紧急协议有触发条件硬性定义，防止"紧急膨胀"。

### 2.2 分级双通道

Agent 不猜测任务等级。等级由**人在 task 创建时标注**。执行过程中 Agent 发现实际复杂度与标注不符时：

```
标注偏低 → Agent 自动建议升级（例: "这个改动需要改
            5 个文件而非预期的 2 个，建议升级到 L3"）
          → 人确认 → 升级 + 走完整流程

标注偏高 → Agent 自动降级并释放仪式感
          → 无需人确认（降级不增加风险）
```

### 2.3 全流程分级矩阵

| 阶段 | L0 | L1 | L2 | L3 | L4 |
|------|:---:|:---:|:---:|:---:|:---:|
| **A. 需求澄清** | ❌ | ❌ | 🟡 一句话 | ✅ 简版 | ✅ 完整 |
| **B. 任务拆解** | ❌ | ❌ | 🟡 Plan | ✅ Plan | ✅ Plan+Graph |
| **C. TDD 门控** | ❌ | 🟡 回归测试 | ✅ 单测 | ✅ 单测+集成 | ✅ 全量 |
| **C. Scope 门控** | ✅ 自动 | ✅ 自动 | ✅ 自动 | ✅ 自动 | ✅ 自动 |
| **C. 两阶段审查** | ❌ | ❌ | 🟡 spec审查 | ✅ 全量 | ✅ 全量 |
| **D. Blueprint 验证** | ❌ | ❌ | ❌ | ✅ | ✅ |
| **D. UI Control 验证** | ❌ | ❌ | 🟡 截图 | ✅ smoke | ✅ 全场景 |
| **D. Final Wave** | ❌ | ❌ | ❌ | 🟡 单维 | ✅ 四维 |
| **E. 审查卡片** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **F. 资产更新** | ❌ | 🟡 patch | ✅ 轻量 | ✅ 标准 | ✅ 完整 |

> CI 映射: L0=无CI / L1=build / L2=build+test / L3=Blueprint+build+test / L4=全套(Blueprint+UI+E2E+build+test)

### 2.4 安全扫描（全级别强制 + 全覆盖）

唯一不分场景的约束。无论什么级别，pre-commit 自动跑：

```bash
# === 内容级（regex） ===
sk-[a-zA-Z0-9]{32,}              # Anthropic key
ghp_[a-zA-Z0-9]{36}              # GitHub PAT
AKIA[0-9A-Z]{16}                 # AWS access key
-----BEGIN.*PRIVATE KEY          # SSH/PEM private key
https://hooks\.slack\.com/       # Slack webhook
https://open\.feishu\.cn/        # 飞书 webhook
discord\.com/api/webhooks/       # Discord webhook
eyJ[a-zA-Z0-9_-]+\.              # JWT token pattern

# === 文件级 ===
.env / .env.local / .env.production → 🚫 拒绝提交
*.pem / *.key / *.p12 / *.pfx → 🚫 拒绝（除非在白名单）
credentials.json / service-account.json → 🚫 拒绝

# === 依赖级 ===
npm audit --audit-level=critical → 🚫 禁止引入已知 critical CVE
cargo audit → 🚫 同上
```

命中 → 拒绝提交，报告具体文件+行号。紧急协议下安全扫描**仍然执行但降级为 warning**（不阻塞提交），事后 24h 内必须处理。

---

## 三、需求澄清重构 — 从"人填表"到"Agent 对话驱动"

**当前问题**：TianYuan 的 Phase -1 Gates 是**人驱动的**——人写方案对比、设计审批、声明 Graph-Verifiability。人还没开始写代码就已经累了。

**Superpowers 的做法完全相反**——brainstorming skill 自动触发，**Agent 主导对话**：

```
人: "我想加暗色模式"

Agent（自动触发 brainstorming）:
  1. 不直接开始编码，后退一步
  2. 问: "所有面板都暗色，还是只有编辑器？"
  3. 追问: "切换按钮放哪里？要记住用户偏好吗？"
  4. 追问: "要不要跟系统主题联动？"
  5. 分段确认，小步快跑
  6. 最后: "所以你确认 A+B+C，不做 D+E，对吗？"
```

**TianYuan 改造后的两通道**：

```
┌─ L0-L2: "聊两句就行" ─────────────────────┐
│  Agent 自动启动 brainstorming 模式（≤3轮）   │
│  → 产出: 一句话需求 + 边界清单               │
│  → 人: 回答 2-3 个问题 → "OK 开始吧"         │
└────────────────────────────────────────────┘

┌─ L3-L4: "聊透 + 人工签字" ──────────────────┐
│  先走 brainstorming（同 L0-L2）               │
│  聊透后 → Agent 提炼:                        │
│    · 需求规格（自然语言）                     │
│    · 边界清单（做/不做）                      │
│    · 设计决策（选了 A 没选 B，为什么）         │
│    · Blueprint Target Graph                 │
│  → 🔑 人工签署确认后，Target 才成为验证基准     │
│  → 前 3 个 commits 内发现偏离 → 允许回退澄清   │
│  → 对话 >5 轮未收敛 → 强制升级到上一级         │
└────────────────────────────────────────────┘
```

> **v3 新增**：L3-L4 的 Blueprint Target **必须有人的 scope lock 确认**。防止 Agent 理解偏差生成错误 Target，验证越严密偏移越隐蔽。

---

## 四、执行精度（取 Chrys 之长）

**Chrys Code Agent 的 7 条精度约束**，按场景分级应用：

| 约束 | L0 | L1 | L2 | L3 | L4 | 作用 |
|------|:---:|:---:|:---:|:---:|:---:|------|
| **SCOPE** | ✅ | ✅ | ✅ | ✅ | ✅ | diff 中出现范围外文件 → 标记 scope creep |
| **READ** | ❌ | 🟡 | ✅ | ✅ | ✅ | 改前先读，不懂就拒绝 |
| **DILIGENCE** | ❌ | 🟡 | ✅ | ✅ | ✅ | 改函数→查所有调用点。修 bug→搜同类模式 |
| **VERIFY** | ✅ | ✅ | ✅ | ✅ | ✅ | 改完跑 lint+test，失败不报完成。L1+ 每 step 显式声明 `[Step] → verify: [check]` |
| **REVIEW** | ❌ | ❌ | 🟡 | ✅ | ✅ | 完成后 30 秒自查 diff |
| **SIMPLE** | ✅ | ✅ | ✅ | ✅ | ✅ | 不引入新抽象/工具函数除非 task 明确要求。自检："资深工程师会说过度复杂吗？"→是就简化 |
| **CASCADE** | ❌ | ❌ | 🟡 | ✅ | ✅ | 改初始化→查清理路径，反之亦然 |

**LastWords 上下文交棒**（取 Chrys）：
- context 使用超过 70% 时触发
- 生成结构化笔记：原始 task / 已完成 / 当前进度 / 剩余 / 决策 / 死胡同
- 新 Agent 从笔记恢复，不丢进度

**Phase 失败升级**（取 GSD）：
- Agent 执行 task 失败 2 次 → 自动升级到更强模型（如 Haiku→Sonnet→Opus）
- 与成本路由互补：日常用低成本模型，搞不定才用贵的

---

## 五、信息资产维护体系

### 5.1 六项目怎么做（含评分）

| 项目 | 亮点 | 软肋 | 综合 |
|------|------|------|:---:|
| OpenSpec | 生命周期最清晰（Delta→合并→归档） | 只管理 spec | 6.0 |
| ECC | 唯一有持续学习（Instinct） | 信息爆炸 763 文档 | 4.2 |
| Chrys | 极简有效（CLAUDE.md 始终最新） | 规模受限 | 4.4 |
| Superpowers | worktree 合并即清理 | 无资产回归 | 4.6 |
| TianYuan | 决策记录表独有 | 全部手动 | 4.0 |
| GSD | — | 几乎无资产概念 | 1.8 |

### 5.2 TianYuan 三层改进

**Layer 1: 自动创建（零人力）**

| 资产 | 创建时机 |
|------|---------|
| 审查卡片 | 每个 task 完成自动产出 |
| Blueprint diff | L3+ 每次验证自动入档 |
| 决策记录 | 会话结束 Hook 自动提取"选择了 X 而不是 Y"类决策 |

**Layer 2: 回归关联（两层分治）**

| 子层 | 能力 | 依赖 |
|------|------|------|
| **Layer 2a** | Blueprint Target → Plan Doc 同步检测（Target 变化→Plan 是否更新？） | 现有 Blueprint Graph（正向） |
| **Layer 2b** | 代码变更 → 受影响文档提醒（grep-based + LLM 辅助标注） | `code-review-graph` + LLM |

> **v3 修正**：Layer 2 不再单方面依赖 Blueprint Graph 反向用。2a 用现有能力，2b 用更通用的方案。

**Layer 3: 生命周期**

```
draft ─人确认→ active ─代码变更→ deprecated ─不再引用→ archive
                    │                      │
                    │ 自动检测             │ 自动提醒
                    ▼                      ▼
               🟡 待确认          "对应代码已变更 N 处，
                                  请确认是否仍然有效"
```

**资产类型 × 维护策略**

| 资产类型 | 格式 | 创建 | 更新触发 | 过期条件 | 归档 |
|---------|------|------|---------|---------|------|
| 审查卡片 | JSON（`cards/task-{id}.json`） | task 完成自动 | 不可变 | N/A | 随 task 归档 |
| 设计文档 | Markdown + YAML frontmatter | L3+ 开工前 | 同模块代码变更 ≥3 文件 | 模块删除 | `archive/` |
| 决策记录 | YAML（`decisions/{date}-{slug}.yaml`） | 会话结束自动 | 被新决策覆盖 | 决策被推翻 | 保留历史 |
| Blueprint diff | JSON | L3+ 每次验证 | 不可变（快照） | N/A | 随 change 归档 |
| Plan 文档 | Markdown | L3+ 规划阶段 | 不可变 | task 全部完成 | 自动归档 |

> **v3 新增**：每种资产定义了存储格式和检索方式。审查卡片用结构化 JSON 而非 Markdown 堆砌，可按模块/时间/严重级别检索。

### 5.3 紧急补记队列

```
.sisyphus/pending-L6.md

每次走 🚨 紧急协议:
  → 自动追加一行: "[timestamp] [session_id] [简述] [deadline: 24h]"
  
每次新会话启动:
  → Agent 自动检查 pending-L6.md
  → 有未处理项 → 提醒用户补记
  → 补完后标记 ✅ 并记录补记时间
```

---

## 六、Agent 工具基础设施 — 让 Agent 专注核心，不被"找代码"拖垮

### 6.1 问题

当前 Agent 执行 task 的典型 context 消耗分布：

```
┌─────────────────────────────────────────────────────┐
│ Agent 执行一次 task 的 context 消耗                   │
│                                                     │
│ ██████████████████░░░░░░ 60%  搜索/读取代码           │
│ ██████░░░░░░░░░░░░░░░░░ 20%  理解上下文/过滤噪音       │
│ ████░░░░░░░░░░░░░░░░░░░ 10%  实际编写代码              │
│ ██░░░░░░░░░░░░░░░░░░░░░  5%  运行测试/lint            │
│ █░░░░░░░░░░░░░░░░░░░░░░  5%  产出报告                 │
└─────────────────────────────────────────────────────┘

核心矛盾: Agent 把 60% 的 token 花在了"找东西"上，
         而这部分完全可以通过工具基础设施来压缩。
```

**传统 SDD 的问题**：花大量精力在流程约束上（你要按什么步骤走、要产什么文档），但没给 Agent 配好工具让它高效执行。结果就是 Agent 做了很多"体力活"——逐文件 grep、逐段 read、从大量结果中筛选。

### 6.2 目标：把"找代码"从 60% 压到 20%

```
理想状态:
┌─────────────────────────────────────────────────────┐
│ ██████░░░░░░░░░░░░░░░░░ 20%  搜索/读取（工具代劳）     │
│ ██████░░░░░░░░░░░░░░░░░ 20%  理解上下文              │
│ ████████████████░░░░░░░ 40%  实际编写代码（提升 4 倍）  │
│ ████░░░░░░░░░░░░░░░░░░░ 10%  测试/验证               │
│ ██████░░░░░░░░░░░░░░░░░ 10%  产出报告                │
└─────────────────────────────────────────────────────┘
```

### 6.3 TianYuan 已有的工具资产

| 工具 | 能做什么 | 当前 Agent 用了吗？ |
|------|---------|:---:|
| **Blueprint Graph** | 从代码提取结构图（IPC contract、节点/边/调用链） | ❌ 只用于事后验证，Agent 执行时不查 |
| **code-review-graph MCP** | 语义搜索节点、影响半径分析、调用者/被调用者查询 | ❌ 工具已就绪，但 Agent prompt 里没让用 |
| **LSP** | 跳转到定义、查找所有引用、文档符号搜索 | 🟡 有 LSP 工具但 Agent 不主动用 |
| **Graphify** | 项目级聚簇知识图谱、God Nodes、社区结构 | 🟡 建了图但 Agent 不查 |
| **grep/glob** | 文本搜索、文件名匹配 | ✅ Agent 最常用的方式（但最低效） |

**核心矛盾**：TianYuan 的"找代码"能力很丰富（Blueprint Graph 的结构抽取 + code-review-graph 的影响分析 + LSP 的精确跳转），但 Agent 还是只用 grep/glob——因为没人告诉它可以用更好的工具。

### 6.4 缺失的能力

| 缺失能力 | 为什么重要 | 对标项目 |
|---------|-----------|---------|
| **语义级代码搜索** | Agent 说"找一个处理用户认证的函数"，工具能直接返回相关函数列表（而非 Agent 自己 grep "auth" 然后从 50 个结果里筛选） | Chrys Explore agent |
| **上下文预加载** | Task 开始时自动加载相关文件到 Agent 初始上下文，减少首轮搜索 | ECC hooks |
| **影响面预分析** | 改一个函数之前，工具自动返回"哪些地方会受影响"（而非 Agent 自己 grep 调用点） | code-review-graph（已有但 Agent 不用） |
| **模式匹配推荐** | Agent 要做某类改动时，工具返回代码库中的已有类似模式（而非 Agent 自己找 best practice） | Superpowers skill 匹配 |
| **代码结构速览** | 打开一个不熟悉的模块时，工具直接返回结构摘要（导出函数/类、依赖关系、测试文件位置） | Blueprint Graph（已有但 Agent 不用） |

### 6.5 改造方案：Agent Code Understanding 层

在 Agent 和代码之间加一个薄层，让搜索从"Agent 命令式"变成"语义查询式"：

```
Before（Agent 命令式，低效）:
  Agent: grep "theme" → 50 个结果 → read 逐个筛选 → 找到 ThemeStore
         耗时: 3-4 轮工具调用，消耗大量 context

After（语义查询式，高效）:
  Agent: find "暗色模式切换的代码位置"
  工具层 → Blueprint Graph 查 frontend contract → code-review-graph 语义搜索
         → 返回: [ThemeStore (stores/theme.ts:15-80), ThemeToggle (components/ThemeToggle.vue),
                  主题变量 (theme.css:1-30), Tauri theme event (src-tauri/theme.rs:42)]
         耗时: 1 轮调用，返回精确结果
```

**具体改造**：

| 阶段 | 改动 | 效果 |
|------|------|------|
| **Wave 1** | Agent prompt 注入工具使用指南：什么时候用 Blueprint Graph、什么时候用 code-review-graph、什么时候用 LSP | 立即生效，让 Agent 用已有能力 |
| **Wave 2a** | 统一查询入口：一个 `query_codebase(natural_language)` 工具，底层路由到 Blueprint/code-review-graph/LSP/grep | Agent 不需要知道底层工具，一句话查询 |
| **Wave 2b** | 上下文预加载 Hook：task 开始时自动分析 Plan Target → 预加载相关文件 → 注入 Agent 初始上下文 | 减少 50% 首轮搜索 |
| **Wave 3** | 影响面预分析：改函数前工具自动返回影响范围（取 code-review-graph） | 替代 Agent 手动 grep 调用点 |
| **Wave 4** | 模式匹配推荐：基于 Blueprint Graph 的社区聚类，推荐已有类似实现 | Agent 不需要"自己找 best practice" |

### 6.6 与场景分级的关系

| 场景 | Agent 工具使用 |
|:---:|------|
| L0 | 不需要搜索工具（已知改哪一行） |
| L1 | 轻量：LSP 跳转定义 + 查找引用 |
| L2 | 标准：code-review-graph 影响面 + Blueprint 查结构 |
| L3 | 深度：全工具链 + 上下文预加载 |
| L4 | 深度 + 模式匹配推荐 + 影响面预分析 |

---

## 七、审查卡片

**格式**（JSON schema）：

```json
{
  "id": "card-{task_id}",
  "level": "L3",
  "summary": "做了什么（一句话）",
  "decisions": [
    {"what": "选了方案 A 而不是 B", "why": "一行理由"},
    {"what": "保持了原有接口", "why": "避免破坏兼容"}
  ],
  "changes": [
    {"file": "src/a.ts", "line": 42, "desc": "参数从 string→Options"},
    {"file": "src/b.ts", "line": 15, "desc": "新增默认值 fallback"}
  ],
  "verification": {
    "lint": "pass",
    "build": "pass", 
    "tests": "12/12",
    "blueprint": "0 deviations",
    "security": "pass"
  },
  "timestamp": "2026-05-03T12:00:00Z",
  "module": "theme"
}
```

**人审查卡片只需确认**：一句话对不对？三个决策有没有问题？变动清单有没有遗漏？全文细节在卡片下方折叠，需要时展开。

---

## 八、可视化输出（降级版）

**v3 修正**：从"自动生成 wireframe"降级为可落地的两个层次：

| 层级 | 输出 | 依赖 |
|------|------|------|
| **Wave 2** | Mermaid flowchart / sequence diagram（从 Plan 中提取调用链自动生成） | LLM + Mermaid 渲染（已有 `mermaidRenderer.ts`） |
| **Wave 4（可选）** | 基于 UI Control Plane 的低保真截图对比（拍当前界面 + 叠加标注说明改动点） | UI Control Plane snapshot |

> **理由**：全自动 wireframe 生成依赖开放研究级能力，降级后 Wave 2 可交付，Wave 4 再探索。

---

## 九、成本路由（取 GSD ~ Phase 失败升级）

| 级别 | 默认模型 | 失败 2 次后升级 | 说明 |
|:---:|---------|:---:|------|
| L0 | Haiku 级 | → Sonnet | typofix 不需要强模型 |
| L1 | Haiku 级 | → Sonnet | 小 bug 根因明确 |
| L2 | Sonnet 级 | → Opus | 可能有模糊性 |
| L3 | Sonnet 级 | → Opus | 跨模块需要更强推理 |
| L4 | Sonnet/Opus | → 人工 | 大型任务保留人工决策 |

---

## 十、冲突管理 & 多 Agent 协调

**等级交叉规则**：两个 Agent 同时修改同一模块时（低等级 vs 高等级）：
- 低等级任务不能修改高等级任务 active scope 中的文件
- 需要修改时 → 走 🚨 紧急协议绕过，或等待高等级任务完成
- Worktree 协议下，不同分支互不阻塞，合并时 blueprint diff 自动检测冲突

---

## 十一、改进路线图（修订版）

```
Wave 1（启动）                  Wave 2a（核心）            Wave 2b（扩展）
┌──────────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ 五级场景分级矩阵       │    │ Agent prompt 工程     │    │ 可视化输出           │
│ 重构模式 + 紧急协议    │    │ 执行精度 7 约束       │    │（Mermaid + UI快照     │
│ 分级双通道（人标+自修） │    │ 审查卡片自动产出      │    │  渐进式）             │
│ 安全扫描（全覆盖）     │    │ 资产生命周期管理      │    │ 成本路由             │
│ 需求澄清重构           │    │ Agent 工具使用指南     │    │ 多 Agent 冲突协议    │
│（brainstorming 模式）  │    │（注入 prompt）        │    │ Phase 失败升级       │
│ L6 补记队列            │    │                      │    │ 统一查询入口         │
└──────────────────────┘    └──────────────────────┘    └──────────────────────┘

Wave 3（完善）                  Wave 4（可选）
┌──────────────────────┐    ┌──────────────────────┐
│ 资产自动回归          │    │ 可视化低保真截图对比   │
│ （Layer 2a + 2b）     │    │ Instinct 持续学习     │
│ LastWords 交棒        │    │ Mutation tracking     │
│ Rollback 快照         │    │ Agent Profile YAML    │
│ Change Proposal 粒度   │    │ 模式匹配推荐          │
│ 上下文预加载 Hook      │    │                       │
│ 影响面预分析           │    │                       │
└──────────────────────┘    └──────────────────────┘
```

> **v3 核心变更**：Wave 2 拆分为 2a+2b，避免过载。可视化降级，回归和 Instinct 推后。

---

## 十二、不改的（核心竞争力）

| 机制 | 理由 |
|------|------|
| Blueprint Graph | 独有，设计保真唯一自动化方案 |
| UI Control Plane | 独有，可视化对比的基础设施 |
| Design Sync 理念 | 方向正确，从手动升级为自动回归（Layer 2a） |
| Final Wave（L4+） | 大型任务的多 Agent 验收仍然必要 |
| Worktree 并行 | 独有且有效，补充多 Agent 冲突协议 |

---

## 十三、效果预期

```
改进前 TianYuan (6.1)              改进后 TianYuan (预估 7.5+)
┌────────────────────┐              ┌────────────────────┐
│ A ████████░░ 6.3   │              │ A █████████░ 7.0   │ ← brainstorming
│ B ██████████ 8.0   │              │ B ██████████ 8.0   │ ← 保持
│ C █████░░░░░ 5.0 ⚠ │              │ C █████████░ 7.5   │ ← 精度约束+成本路由+失败升级
│ D ███████░░░ 6.5   │              │ D █████████░ 8.0   │ ← +mutation track(L4)
│ E ███████░░░ 5.8   │              │ E ████████░ 7.5   │ ← 审查卡片+全安扫+资产回归
└────────────────────┘              └────────────────────┘

最大跃升: C(5.0→7.5) + E(5.8→7.5)
总分: 6.1 → 7.6
```

---

> **v3 一句话**：五级场景各走各的路，重构是模式不是等级，紧急有定义有补记，Agent 不问人只对话，
> Target 生成人工签字，资产有格式有生命周期，代码改了自动提醒哪些文档该更新。

---

## 附录 A、典型失败模式（反例 → 正例）

> 源自 Karpathy 对 LLM 编程问题的观察 + TianYuan 实战踩坑。Agent 执行 task 时对照自查。

### A.1 静默假设（违反 Think Before Coding）

❌ **Agent 直接写了**
```
用户: "加一个导出功能"
Agent: （实现 JSON + CSV + 分页 + 后台任务通知，200 行）
```

✅ **Agent 先追问**
```
> 我的理解: 加一个导出按钮
> 边界: ✅ 只导出当前列表 / ❌ 不做分页导出 ❌ 不做后台任务
> 需要你确认: 导出格式要 JSON 还是 CSV？
```

### A.2 过度设计（违反 Simplicity First）

❌ **为一个折扣计算引入 Strategy Pattern + Config + Validator，150 行**

✅ **一个函数解决，5 行**。等真正需要多种折扣类型时再重构。

**自检**: "一个资深工程师会说过度复杂吗？" → 是就简化。

### A.3 顺手改无关代码（违反 Surgical Changes）

❌ **修空指针 bug 时顺便**：改了注释措辞、加了 type hints、重命名了变量、添加了 docstring

✅ **只改空指针的那一行**。每行改动必须能追溯到用户请求。发现无关死代码 → 提及但不删除。

### A.4 模糊目标（违反 Goal-Driven Execution）

❌ **"修一下认证系统"** → Agent 开始改代码，改完发现不是用户想要的

✅ **显式声明验证条件**:
```
1. 写测试: 改密码 → 验证旧 session 失效
   verify: 测试复现 bug（红灯）
2. 实现: 密码变更时清除 session
   verify: 测试通过（绿灯）
3. 跑全量 auth 测试
   verify: 零回归
```
