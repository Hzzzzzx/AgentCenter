# AGENTS.md — TianYuan SDD v.next

> 渐进式加载：核心规则在此文件（每次会话必读），详细流程见链接文档（按需读取）。
> 原则：分场景不搞一刀切 · Agent 主导对话 · 精度约束 · 工具优先 grep

---

## 总览：流程与信息分层

**核心理念**：Specification-Driven Development（SDD）——规格是 source of truth，代码服务于规格。

TianYuan SDD 的主流程：

```
需求澄清 → 方案对比 → 设计审批 → 计划 → 执行 → 审查 → CI/CD → 归档
                                  ↑
                          【Design Gate 硬性门控】
```

PRD / HLD / LLD 是 SDD 的信息分层，不是额外三套重文档：

| 分层 | 回答什么 | 对应 SDD 阶段 | 核心产物 |
|------|----------|---------------|----------|
| PRD / 需求边界 | 做什么？不做什么？验收标准是什么？ | 需求澄清 | 一句话理解、边界清单、人工确认 |
| HLD / 概要设计 | 整体怎么设计？模块和数据流怎么走？ | 方案对比、设计审批、计划 | 方案取舍、模块边界、Blueprint Target Graph、关键决策 |
| LLD / 实现计划 | 具体改哪些文件/函数？影响面和测试点是什么？ | 计划、执行 | task list、GitNexus/图谱影响面、文件级计划、回归测试 |
| Verification / 证据归档 | 怎么证明做对了？ | 审查、CI/CD、归档 | lint/test/build、Blueprint diff、UI Control、审查卡片、Design Sync |

### Constitution 原则

1. **Test-First**：L2+ 优先写或明确回归测试，失败后再写实现。
2. **Simplicity**：优先简单方案，避免过度设计。
3. **Integration-First**：关键链路优先真实环境或端到端验证。
4. **Lint-First**：写完代码后运行 lint / 类型检查 / 对应测试。

---

## 快速参考：我该怎么做？

| 我在做什么 | 我的级别 | 需要做什么 |
|-----------|:---:|-----------|
| typo / 注释 / 常量改名 | **L0** | 改完 → 安全扫描 → 跑 lint → 审查卡片 → 完。5 分钟。 |
| 小 bug（根因明确，1-3 文件） | **L1** | L0 + 回归测试 |
| 小功能（1-5 文件，需求清晰） | **L2** | 先 brainstorming → 确认边界 → L1 + TDD 单测 |
| 中型功能（3-10 文件，有设计选择） | **L3** | L2 + Plan + Blueprint 验证 + 两阶段审查 |
| 大型功能（8+ 文件，新模块/跨系统） | **L4** | L3 + Final Wave 四维验收 + UI Control 全场景 |
| 重构（行为不变，结构变） | 🔄 叠加在 L1-L4 上 | Plan 必须声明 Expected Contract Changes |
| 🔴 线上挂了 | 🚨 紧急 | 先修！安全扫描降为 warning。24h 内补审查卡片+回归测试 |

> 详细矩阵：[场景化全流程](docs/reference/sdd-improvement-plan.md#23-全流程分级矩阵)

---

## 行为约束（每次执行 task 必遵守）

```
1. [SCOPE]   只改 task scope 内的文件。diff 中出现范围外文件 → 报告 scope creep。
2. [READ]    L2+: 改任何文件前先 read。不了解代码就改 → 拒绝执行。
3. [DILIGENCE] L2+: 改了函数/类型/导出 → grep 所有引用点，确认兼容。
4. [VERIFY]  改完最后一个文件 → 跑 lint + 测试。失败不报告完成。
   L1+ 每个 step 显式声明验证条件:
     [Step] → verify: [具体检查]
   示例: "新增 API 参数" → verify: "tsc 零错误 + 相关单测通过"
5. [REVIEW]  L3+: 完成后留 30 秒自查 diff，逐条过，确认无遗漏。
6. [SIMPLE]  不引入新的抽象/工具函数/类型，除非 task 明确要求。
   自检口诀: "一个资深工程师会说这段代码过度复杂吗？" → 是就简化。
7. [CASCADE] L2+: 改了初始化 → 检查清理路径。改了清理 → 检查恢复。
```

> L0 只需 SCOPE + VERIFY + SIMPLE。L6 只需 VERIFY。

**底线**：`as any`、`@ts-ignore`、空 catch 块、删除失败测试、提交 secrets → 禁止。

---

## 需求唤起模式（收到任何功能请求时）

收到功能请求后，**不要直接写代码**。按以下步骤：

```
1. [后退] 先搞清楚真正要解决什么。
2. [追问] L2+: 2-3 个问题澄清边界: 做什么？不做什么？有约束吗？
3. [分段] 把设计分成可消化的小段，每段确认后再往下。
4. [总结] 用一句话 + 边界清单让用户确认。
5. [不做] 用户确认前不写代码、不画架构、不给方案。

输出格式:
  > 我的理解: [一句话]
  > 边界: ✅做 / ❌不做
  > 需要你确认: [最多 1-2 个问题]
```

L0/L1 追问可跳过。L3/L4 追加：对话 → 自动提炼 Plan + Blueprint Target → **必须人工签署后执行**。

---

## 审查卡片（每个 task 完成必产出）

```json
{"summary": "做了什么（一句话）",
 "decisions": [{"what": "选了 X 而非 Y", "why": "一行理由"}],
 "changes": [{"file": "path", "desc": "改了什么"}],
 "verification": {"lint":"pass","build":"pass","tests":"n/n","blueprint":"n deviations"}}
```

> L0/L1 简化版（summary + changes 即可）。卡片写完后，人只需确认 summary 和 decisions，全文细节在下方折叠。

---

## 代码理解工具（找代码时）

**MANDATORY**: 找代码时，按此优先级选择工具：

| 我要做什么 | 首选工具 | 为什么 |
|-----------|---------|--------|
| 了解某个函数/类在哪 | `semantic_search_nodes` | 语义搜索，比 grep 精准 |
| 改函数前查影响面 | `get_impact_radius` | 不占 context，比 grep 快 |
| 追溯调用链 | `query_graph` (callers_of) | 结构化的，不会漏 |
| 了解架构 | `get_architecture_overview` | 社区聚类，一眼全局 |
| 找文件名匹配 | `glob` | 精确匹配 |
| 搜文本内容 | `grep` | 最后手段 |

**不要用 grep/read 去发现你已经可以通过图工具一次拿到的东西。**

> 详细：[代码理解工具使用指南](docs/reference/sdd-improvement-plan.md#六agent-工具基础设施)

---

## 任务管理

- L0 + L1: 直接改，不需要 Plan。
- L2+: 创建 todo list，标记 `in_progress` → `completed`。
- L3+: Plan Markdown 必须按 PRD / HLD / LLD / Verification 分层，并声明 Blueprint Target Graph（人工签署后方可执行）。
- 任务级别在创建时**人标注**。执行中 Agent 发现实际复杂度不符 → 自动建议升级/降级。

### 角色与触发方式

| 角色 | 用途 | 触发方式 |
|------|------|----------|
| Prometheus | 规划者 | 任务规划 |
| Metis | 计划审查前检查 | 计划评审 |
| Momus | 计划质量审核 | 计划审核 |
| Atlas | 执行者 | 任务执行 |
| Oracle | 架构咨询、调试 | 问题咨询 |

### 项目级状态

```
.sisyphus/
├── plans/           # 活跃计划
├── archive/         # 已完成归档
├── boulder.json     # 活跃计划追踪
├── cards/           # 审查卡片
└── sdd-issues-checklist.md  # SDD 使用效果问题清单
```

---

## 过程文档（Checkpoint）

- L0-L1: 不需要过程文档。审查卡片就是唯一产出。
- L2: 审查卡片 + 轻量 Plan（如有）。
- L3+: Plan + Blueprint diff + 审查卡片 + 决策记录。**这些文件是 Agent 的"外部记忆"，用于跨 session 恢复进度。**
- 紧急协议: 24h 内补以上全部。

所有文档写入 `docs/{module}/` 或 `.sisyphus/`。会话结束自动执行 Design Sync。

---

## 不改的

- Blueprint Graph — 唯一自动契约验证
- UI Control Plane — 唯一 UI 层验证
- Design Sync 三层防线 — 决策不丢失
- Final Wave（L4）— 大型任务多 Agent 验收
- Worktree 并行 — 独有且有效

---

## 去哪里找细节

| 我需要 | 去哪里 |
|--------|--------|
| 完整场景分级矩阵 | [SDD 改进方案 v3](docs/reference/sdd-improvement-plan.md#23-全流程分级矩阵) |
| 资产维护体系 | [SDD 改进方案 v3 §五](docs/reference/sdd-improvement-plan.md#五信息资产维护体系) |
| 六项目横向对比 | [横向对比文档](docs/reference/sdd-cross-project-comparison.md) |
| 评价框架 | [评价框架文档](docs/reference/sdd-evaluation-framework.md) |
| SDD v1.1 图验证 | [SDD v1.1](docs/sdd/sdd-v1-1-graph-verified-development.md) |
| Blueprint Graph | [Blueprint Graph 架构](docs/sdd/blueprint-graph-architecture.md) |
| Worktree 协议 | [Worktree 协议](docs/worktree-protocol.md) |
| 设计文档模板 | [设计文档模板](docs/design-doc-template.md) |
| 项目文档索引 | [docs/index.md](docs/index.md) |

---

> 本文件是 TianYuan Agent 的行为合同。每次会话必读。改动需人工确认。

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **TianYuan** (33863 symbols, 60257 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/TianYuan/context` | Codebase overview, check index freshness |
| `gitnexus://repo/TianYuan/clusters` | All functional areas |
| `gitnexus://repo/TianYuan/processes` | All execution flows |
| `gitnexus://repo/TianYuan/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

---

## Knowledge Graph: graphify

**IMPORTANT**: Run `/graphify` or `graphify --update` after completing significant feature work.
Read `graphify-out/GRAPH_REPORT.md` for god nodes and architecture context before starting work on unfamiliar modules.

- **Starting work on a new module**: Read GRAPH_REPORT god nodes and communities
- **After major feature completion**: Run `graphify --update` to capture new relationships
- **If GRAPH_REPORT is missing, blank, clearly outdated, or inconsistent with the current module layout**: run `/graphify` or `graphify --update` before using it as architecture evidence.

---

## SDD v1.2 Tool-Assisted Development

See `docs/sdd/sdd-v1-1-graph-verified-development.md` for the complete workflow.

| Stage | Tool | Purpose |
|-------|------|---------|
| Before development | **graphify** | Read architecture overview |
| During development | **code-review-graph** | Continuous impact monitoring (hooks) |
| During development | **GitNexus** | Deep impact analysis + execution flows |
| After development | **Blueprint Graph** | Plan-vs-code contract verification |
| After verification | **graphify** | Final refresh to capture completed state |

### Code Graph Freshness Gate

图谱工具的空结果只能说明"当前图谱没有证据"，不能直接说明"代码没有影响面"。出现以下任一情况时，先刷新或重建对应图谱，再继续判断：

| 场景 | 刷新动作 | 刷新后必须做什么 |
|------|----------|------------------|
| GitNexus 明示 stale、commit 不一致、符号/变更明显存在却查不到 | `npx gitnexus status` → `npx gitnexus analyze`（必要时 `--force`） | 重跑 `impact/context/query/detect_changes`，再报告 blast radius |
| GitNexus 对新增、改名、移动后的 symbol 返回空或候选明显不对 | `npx gitnexus analyze` | 用 `context`/`impact` 复核；仍为空时记录工具问题并 fallback 到 LSP/rg/tests |
| code-review-graph 的 `detect_changes` / `get_impact_radius` 与 git diff 明显不一致 | 先确认 hooks/索引是否可用；必要时重跑 postprocess 或项目图谱刷新命令 | 重跑 detect/impact；仍异常时不要把结果作为唯一审查依据 |
| `graphify-out/GRAPH_REPORT.md` 缺失、过旧、空图、god nodes 与当前模块明显不符 | `/graphify` 或 `graphify --update` | 重新读取 GRAPH_REPORT，再进入架构判断 |
| L3+ Plan 的 target graph、Actual Graph 或 Blueprint diff 早于最后一次代码修改 | 重新生成 Actual Graph 并跑 Blueprint check | 审查卡片记录最新 diff/report 路径 |

恢复失败时的兜底规则：明确写出"图谱证据不可用/不可信"，改用 LSP、`rg`、测试和人工 diff 自查补证，不能静默通过。
