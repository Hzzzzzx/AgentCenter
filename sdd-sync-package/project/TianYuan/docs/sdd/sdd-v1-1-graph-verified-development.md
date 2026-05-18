# SDD v1.1 Specification + Graph-Verified Development

> 状态：Normative Spec（规范）
> 创建日期：2026-04-26
> 关联：`.sisyphus/plans/`、`.sisyphus/evidence/`

---

## 概述

SDD v1.1 在 SDD 原则（规格是 source of truth）基础上，引入 Blueprint Graph 作为规格与代码之间的**可计算验证层**。

规格是 source of truth，Blueprint Graph 是对规格的结构化表达，两者不是替代关系。Blueprint Graph 提取计划任务卡中声明的目标调用链，Actual Graph 从代码中抽取对应的实际调用链，Diff/Report 对齐二者并生成差异证据。审查和 Final Wave 优先读 diff/report，再读代码。

v1.1 不引入 SQLite 存储、MCP 接口、产品 UI、CI 阻塞 gate 或独立 F5 维度。所有生成物为 JSON/Markdown，存于 `.sisyphus/evidence/`。

---

## 核心理念

SDD v1.1 围绕四个支柱：

**意图明确**：requirements / designs / plans 必须清晰无歧义，模糊的意图无法产生可验证的 target graph。

**结构可计算**：跨层调用链（TS → IPC → Rust / API → Store → Component → Test）必须能用 target graph 声明节点和边，作为后续比对的目标。

**代码可追溯**：Actual Graph 必须从实际代码中抽取，每个节点/边能追溯到文件路径、符号名、注册调用或测试用例。

**审查可约束**：Review Agent 在读 diff/report 之后才读代码，diff/report 是审查的第一入口，不是代码全文。

---

## Source-of-Truth Order

执行和审查必须按以下顺序定位 source of truth：

1. **用户批准的需求和设计决策**
2. `docs/sdd/` 或对应模块的设计文档
3. `.sisyphus/plans/*.md` 任务卡中的 Blueprint target block
4. 生成的 `blueprint.*.json` / `actual.*.json` / `diff.*.json` / `report.*.md` —— 注意：这些是**生成的证据**，不是人类 source of truth
5. **代码实现**

审查结论与 source of truth 不一致时，以编号靠前的条目为准。

---

## Graph-Verified Applicability

| 条件 | 结果 |
|------|------|
| 仅修改文本、样式、注释、单文件 | `Blueprint: N/A` |
| 涉及 TS ↔ IPC ↔ Rust 调用链 | `Blueprint: Required` |
| 涉及 API ↔ Store ↔ Component ↔ Test（v1.2+） | `Blueprint: Required` |
| 涉及 Agent planner / executor / pipeline（v1.2+） | `Blueprint: Required` |
| 涉及 DB / replay / memory / persistence（v1.2+） | `Blueprint: Required` |
| Large / XL 跨模块任务 | 默认 `Blueprint: Required`，除非声明 N/A 理由 |

判断优先级：先看是否跨层，再看是否有可声明的目标调用链。

---

## UI Control Runtime Evidence

Blueprint Graph 验证的是规格链路与静态代码结构；UI Control 验证的是运行时界面是否真实可见、可操作、无明显布局/渲染/runtime 错误。两者互补，不互相替代。

| 条件 | UI Control 结果 |
|------|-----------------|
| 纯后端、纯文档、纯数据迁移且无可见 UI | `UI Control: N/A` |
| 修改 Workbench 壳层、布局、面板、导航、Chat/Expert/Panorama 等核心可见 UI | `UI Control: Required`，至少运行 `pnpm smoke:ui-control` |
| 修改业务模块的可见旅程，如小说 dashboard、导出入口、托管写作面板 | `UI Control: Required`，优先运行模块专用场景；通用 smoke 只能作为壳层健康证据 |
| 场景尚未建设或当前状态不可用 | 允许 waiver/skipped，但必须写明原因、缺失场景和后续补齐项 |

标准 evidence：

```markdown
## UI Control Runtime Verification
- Applicability: Required | Optional | N/A
- Rationale: [一句话说明]
- Scenario command: [pnpm smoke:ui-control / tianyuan-ui run <scenario> / N/A]
- Evidence artifacts: [.sisyphus/evidence/ui-control/...]
- Result: pending | passed | failed | skipped with explanation
- Waiver, if any: [none 或原因]
```

当前采用策略：

- `pnpm smoke:ui-control` 是平台壳层 smoke，适合检查黑屏、runtime error、布局遮挡、关键 UI anchor 丢失。
- 模块功能回归必须逐步建设模块场景，例如 `ui.assert.novel.dashboard`、`ui.assert.novel.export`；没有模块场景时，不得把通用 smoke 解释为模块功能已回归。
- UI Control evidence 默认进入本地验收和 Final Wave，不默认成为 PR 阻塞 CI。只有稳定、可复现、对环境不敏感的场景才能升级为 CI gate。
- UI Control 不替代 unit/integration test、Tauri IPC 测试、真实模型回归或人工体验评审。

---

## Design Gate Adjustment

在现有 Phase -1 Gates 基础上，增加 **Graph-Verifiability Check**：

1. **Is this task cross-layer?**（这个任务是否跨层？）
2. **Is there a call/dependency chain that must be verified?**（是否有必须验证的调用/依赖链？）
3. **If yes, can target graph nodes/edges be declared in the plan?**（如果是的，目标图节点/边能否在计划中声明？）
4. **If not declarable, is it because design is unclear, or because tools don't support it yet?**（如果不能声明，是因为设计不清晰，还是工具尚不支持？）
5. **If design is unclear: do NOT proceed to implementation.**（如果设计不清晰，不进入实现阶段。）

只有工具不支持（而非设计不清晰）时，才允许推进实现，并在 plan 中注明"target graph 待工具支持后补充"。

---

## Plan Template Contract

Medium 及以上计划必须包含 Blueprint Verification block。格式：

```markdown
## Blueprint Graph Verification
- Applicability: Required | Optional | N/A
- Rationale: [一句话说明]
- Supported graph domain: quest | api | agent | persistence
- Target graph source: [路径或"由 plan block 生成"]
- Actual graph source: [抽取命令或路径]
- Evidence artifacts: [.sisyphus/evidence/{plan}/ 下的路径]
- Diff/report status: [pending | passed | failed with explanation]
- Waiver, if any: [none或原因]
```

不适用的计划必须写明：

```markdown
## Blueprint Graph Verification
Applicability: N/A
Reason: [单文件 / 无跨层链 / 纯文本修改 等]
```

---

## Review Gate Adjustment

当前审查顺序：

```
读计划 → 读代码 → 跑测试 → 给结论
```

调整为：

```
读计划 → 读 Blueprint diff/report → 读代码 → 跑测试 → 给结论
```

Phase 1.1 只要求 diff/report 存在并可读，不要求 Review Agent 自动解析图结构。Impact/risk 分析和 review guidance 是未来（v1.2/v1.3）能力，不在 v1.1 强制要求。

如果计划声明 `UI Control: Required`，Review Agent 在读代码前还应读取对应 UI Control evidence summary。若只有通用 smoke 而任务涉及业务模块 UI，必须把“缺模块场景”作为测试缺口记录，不能把它当成完整功能回归。

---

## F1 Plan Compliance Addendum

Blueprint Compliance 是 **F1 Plan Compliance 的证据子检查项**，不构成独立 F5 维度。

F1 checklist 增补：

- Plan 声明了 `Blueprint: Required` 还是 `N/A`？
- `Required` → target graph 是否存在（plan block 或独立 JSON）？
- `Required` → actual graph 是否已生成？
- `Required` → diff report 是否存在？
- `Required` → diff 中是否有未解释的缺失节点/边？
- Report 是否写入 `.sisyphus/evidence/{plan}/`？
- UI Control Required → 是否生成运行时 UI evidence？
- UI Control Required → 若涉及业务模块，是否运行模块专用场景或记录明确 waiver？

**声明**：Blueprint Compliance 不构成独立的 F5 维度，而是 F1 Plan Compliance 的证据子检查项。

---

## Design Sync Addendum

在现有 Design Sync Protocol 基础上，增加 **Blueprint Contract Sync** 检查项：

- 本次实现是否新增 / 移除 / 重命名了跨层节点？
- 本次实现是否新增 / 移除了 IPC / API / Agent / DB / Test 边？
- 如果是，目标 block（design doc 或 plan 中声明的 target graph）是否已同步更新？
- 对应的 evidence report 是否重新生成？

已归档的 plan 不追溯修改。

---

## code-review-graph Staged Adoption

| Capability | SDD Value | v1.1 Decision | v1.2 Status |
|---|---|---|---|
| graph diff | 检测规格-代码不匹配 | **已采用** | — |
| UI Control smoke evidence | 检测运行时 UI | 采用为 F3 evidence | — |
| review guidance | 告诉 Agent 先看哪里 | 采用为 report format principle | — |
| impact radius | 追溯变更影响半径 | 延后到 v1.2/v1.3 | ✅ **已采用**（via code-review-graph MCP） |
| risk scoring | review 优先级排序 | 延后到 v1.3 | ✅ **已采用**（via `detect_changes`） |
| minimal context packet | 减少 Agent review token 消耗 | 延后到 v1.3 | ✅ **已采用**（via `get_minimal_context`） |
| knowledge graph overview | 项目全局架构理解 | 未列入 | ✅ **已采用**（via graphify skill） |
| edge confidence | 区分事实与推断 | 延后到 v2 或 v1.3 | 延后 |
| SQLite/WAL/FTS5 | 持久化 / 增量 / 搜索 | 延后到 v2 | 延后 |
| MCP tools | Agent / IDE 查询接口 | 延后到 v2 | 延后 |

---

## v1.2 Tool-Assisted Development Workflow

> 2026-05-03 增补。将 graphify（全局架构理解）、code-review-graph（影响面分析）和 GitNexus（执行流追踪）正式编入 SDD 开发流程。

### 工具在流程中的位置

```
Phase -1: 需求澄清
    │
    ├─→ 【graphify】首次接触项目/模块时，读 GRAPH_REPORT 建立全局认知
    │        作用：知道核心模块、关键依赖、跨模块耦合
    │
    ▼
Design Gate: 方案对比 → 设计审批
    │
    ▼
Plan: 声明 Blueprint target + 验证策略
    │
    ▼
实现阶段:
    │
    ├─→ 【code-review-graph】每次 Write/Edit 后 hooks 自动增量更新图
    ├─→ 【code-review-graph detect_changes】改代码前/后查看影响面
    ├─→ 【code-review-graph get_impact_radius】改关键函数前看 blast radius
    ├─→ 【GitNexus impact / context】blast radius + 执行流 + 360°符号视图
    ├─→ 【图谱新鲜度】空结果或明显异常时先刷新图谱，再判断影响面
    ├─→ 【LSP】精确定位定义、引用、诊断
    ├─→ 【AST-grep】批量模式检查/替换
    ├─→ 【gitingest】（可选）子 Agent 用便宜模型快速提取代码内容
    │
    ▼
实现完成:
    │
    ├─→ 【graphify --update】刷新知识图谱，捕获新增的模块和关系
    ├─→ 【Blueprint Graph check】验证代码与 Plan 的 target graph 一致
    │
    ▼
Review Gate:
    │
    ├─→ 读 Blueprint diff/report（规格偏移）
    ├─→ 读 code-review-graph detect_changes（影响面 + 风险评分）
    ├─→ 读 graphify GRAPH_REPORT（架构变更概览）
    ├─→ 再读代码
    │
    ▼
Final Wave → CI/CD
    │
    └─→ 【graphify --update】最终刷新知识图谱，归档完整项目状态
    │
    ▼
归档
```

### 五个工具的职责边界

| 工具 | 回答的问题 | 什么时候用 | 刷新频率 | 角色 |
|------|-----------|-----------|---------|------|
| **graphify** | "项目整体在干什么？改了后架构变了吗？" | 接手项目 / 模块完工后 | 按需（`--update`） | 架构师 |
| **code-review-graph** | "这个改动会影响哪些地方？自动跟踪变更" | 每次改代码时 | 自动（hooks 每 Edit 后） | 哨兵 |
| **GitNexus** | "blast radius 多大？走哪条执行流？" | 需深度追踪时 | 按需（`npx gitnexus analyze`） | 侦探 |
| **Blueprint Graph** | "代码和计划一致吗？漏了什么？" | SDD 验收时 | 按需（`check`） | 监理 |
| **gitingest** | "这些文件里具体写了什么？" | 子 Agent 内容采集 | 每次调用 | 采集员 |

> code-review-graph 和 GitNexus 互补：前者 hooks 自动跑做持续监控，后者做深度执行流追踪。

### 代码图谱刷新触发矩阵

原则：图谱工具的空结果只能说明"当前图谱没有证据"，不能直接说明"代码没有影响面"。只要结果与当前工作区、git diff、LSP 定位或肉眼可见代码结构矛盾，就必须先按下表刷新，再继续审查。

| 图谱 | 什么时候必须刷新 | 刷新动作 | 刷新后验收 |
|------|------------------|----------|------------|
| **GitNexus** | `gitnexus://repo/TianYuan/context` 或工具输出提示 stale；`npx gitnexus status` 显示 indexed commit 与当前 commit 不一致；`impact/context/query/detect_changes` 查不到明显存在的符号、变更或执行流；文件移动/重命名/新增导出后候选明显不对；merge/rebase/branch switch 后准备做影响面判断 | `npx gitnexus status` → `npx gitnexus analyze`；仍异常或疑似索引损坏时用 `npx gitnexus analyze --force` | 重跑原查询；对关键 symbol 组合使用 `context` + `impact`；仍为空时记录工具问题，不能报告"无影响"，必须 fallback 到 LSP、`rg`、测试和人工 diff 自查 |
| **code-review-graph** | `detect_changes` / `get_impact_radius` 与 `git diff` 文件列表不一致；改了关键文件但风险报告为空；hook 没有运行迹象；大规模移动/重命名/生成代码后影响面异常偏小；社区/flow 结果明显落后于当前目录结构 | 检查 hooks/索引健康；可用时重跑 `run_postprocess` 或项目图谱刷新命令 | 重跑 `detect_changes` / `get_impact_radius`；报告中必须说明图谱是否可信；仍异常时只作为弱证据 |
| **graphify** | 首次接触陌生模块却没有 `GRAPH_REPORT.md`；`GRAPH_REPORT.md` 缺失、空图、时间过旧；god nodes / communities 与当前模块边界明显不符；完成 L3/L4、大重构、新模块、新跨模块依赖后；重要文档/架构说明批量变更后 | `/graphify` 或 `graphify --update` | 重新读取 `graphify-out/GRAPH_REPORT.md`，确认 god nodes、communities、架构热点与当前工作一致 |
| **Blueprint Graph** | Plan target graph 改过；最后一次代码修改晚于 Actual Graph / diff/report；IPC、frontend contract、agent/persistence 链路变更；Blueprint extractor 或支持 domain 更新；review 发现 report 和代码结构对不上 | 重新生成 Actual Graph，重跑 `pnpm blueprint-graph:check` 或对应 domain check | 审查卡片记录最新 blueprint/actual/diff/report 路径；任何 missing node/edge 都要解释或修复 |

恢复失败时的处理方式：

1. 在审查卡片中写明"图谱证据不可用/不可信"及失败原因。
2. 用 LSP 引用、`rg` 精确搜索、单元/集成测试、manual diff review 补证。
3. 不允许把异常空结果当作通过依据。

### Review Gate 调整

审查顺序从：
```
读计划 → 读 Blueprint diff/report → 读代码 → 跑测试 → 给结论
```

调整为：
```
读计划 → 读 graphify GRAPH_REPORT（架构背景）
       → 读 code-review-graph detect_changes（影响面 + 风险，自动持续）
       → 读 GitNexus context / impact（关键符号深度分析 + 执行流）
       → 读 Blueprint diff/report（规格偏移）
       → 读代码 → 跑测试 → 给结论
```

---

## Explicit Non-Goals

以下条目在 v1.1 中**明确不做**，每一项都是明确的范围边界，不是"暂时不做"：

**No SQLite** — 不在 v1.1 引入 SQLite 存储。Phase 1 使用 JSON 文件作为图数据主存储，待工具链稳定后再评估 SQLite 迁移。

**No MCP** — 不在 v1.1 引入 MCP 接口。MCP 是 v2 讨论范围，v1.1 不定义任何 MCP tool interface。

**No UI** — 不做 TianYuan Workbench / product UI / nebula viewer。可视化是 Phase 2 目标，v1.1 不产出任何 UI 组件或交互视图。

**No CI blocking** — 不把 graph check 变成 CI 阻塞 gate。Rule findings 仅出现在 report 中，不触发 CI 失败，不成为部署前置条件。

**No F5** — Blueprint Compliance 不构成独立 F5 维度。已确定合入 F1 Plan Compliance 作为证据子检查项，F1 即为最终维度，F5 不存在。

**No human YAML** — 不引入人工手写 Blueprint YAML contract。Target graph 由 plan block 生成或工具抽取，人工只维护 plan Markdown。

**No custom rule DSL** — 不引入自定义规则 DSL。Graph 差异判断基于节点/边 existence diff，不支持自定义语义规则。

**No extractor expansion** — 不扩展 extractor 到新 pilot 或新语言。Phase 1 试点限定为 Quest 调用链，暂不覆盖 Chat、其他功能链或新语言。

**No archived plan migration** — 不追溯修改已归档计划。已归档 plan 保持原样，不补充 target graph block，不重新生成 evidence。

---

## Success Criteria

1. Agent 在 planning 阶段能判断哪些任务需要 graph verification。
2. 跨层任务在实现前有清晰的 target contract（plan 中的 Blueprint block）。
3. 审查有机器生成的 diff/report 作为第一入口，减少遗漏。
4. Evidence 可归档、可追溯，支持未来 retrospective 查阅。
5. 无人工维护 YAML 或图数据库的负担。
