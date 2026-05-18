# SDD Blueprint Graph 架构基调

> 状态：Architecture Baseline（基调确认中）
> 创建日期：2026-04-26
> 关联草稿：`.sisyphus/drafts/sdd-system-overhaul.md`、`.sisyphus/drafts/sdd-blueprint-graph-design.md`、`.sisyphus/drafts/sdd-blueprint-graph-reference-corpus.md`

---

## 1. 核心定位

Blueprint Graph 不是一开始就作为 TianYuan 产品里的可视化功能建设，而是先作为 **SDD / Agent 执行基础设施** 建设。

它的第一目标是帮助当前项目中的 Agent 和 SDD 流程：

- 理解目标蓝图与实现边界；
- 检查实现是否偏离计划；
- 把设计、代码、测试、证据串成可查询的图；
- 为 review、Final Wave、归档提供结构化依据；
- 防止长周期开发中出现“做了一截就迷失方向”的问题。

当这套基础设施稳定后，才把它产品化为 TianYuan AI Native IDE 的可视化工作台能力。

---

## 2. 两阶段路线

### 阶段一：SDD / Agent 工具

服务对象：TianYuan 当前开发流程、`.sisyphus` 计划、Agent 实现与审查。

形态可以是：

```text
CLI / scripts
generated JSON graph artifacts
JSON / Markdown reports
Agent review gate input
```

阶段一不追求完整产品 UI，不以"炫酷可视化"为目标，而是先建立稳定闭环。

**Phase 1 范围锁定**：

- **试点限定**：Quest 是 Phase 1 唯一试点链，不扩展到 Chat 或其他功能链。
- **产出格式**：Phase 1 生成 JSON 快照和 Markdown/JSON 报告，不生成 YAML 文件。
- **存储策略**：SQLite 推迟到 Phase 2；Phase 1 使用 JSON 文件作为图数据主存储。
- **YAML 定位**：Phase 1 不要求生成 YAML 作为快照或 review artifact；JSON 是唯一产出格式。
- **语义策略**：Rule findings 仅出现在报告中；默认 CLI 行为不阻塞 CI（内部/工具错误除外）。
- **UI 排除**：Phase 1 不包含 TianYuan Workbench、产品 UI 或分层/nebula 可视化查看器。

Phase 1 回答的核心问题是：

> 对于一条计划的 Tauri IPC 链，实际代码是否包含预期的 TS invoke、Rust command handler 和 Tauri command registration？

```text
目标 / 计划 / 任务
  → Blueprint Graph（目标结构）
  → Actual Graph（代码现实）
  → Evidence Graph（测试与运行证据）
  → Mismatch Report（差异与风险）
```

### 阶段二：TianYuan 产品能力

服务对象：未来 TianYuan 用户、AI Native 对话编辑器工作台、可视化调试与 Agent 编排体验。

产品化形态可以是：

```text
Blueprint Workbench
Architecture Map
Agent Progress Radar
Design → Implementation → Evidence 可视化导航
```

用户可以在 TianYuan 中查看：

- 功能从需求到 UI、前端代码、IPC、Rust 后端、DB、测试证据的实现进度；
- 哪些链路断开；
- 哪些 Agent 正在处理哪些节点；
- 哪些证据已经通过；
- 点击节点跳转到文档、代码、测试、运行证据或对话上下文。

---

## 3. 不偏离初始目标的原则

Blueprint Graph 的建设必须遵守以下顺序：

1. **核心引擎先行**：先让 Agent / SDD 工作流真正受益。
2. **可视化后置**：可视化是洞察层，不是第一阶段的核心目标。
3. **数据准确优先于图形漂亮**：底层图不准，视觉层只会变成装饰。
4. **同一核心，两种外壳**：阶段一被 `.sisyphus` / Agent 使用；阶段二被 TianYuan UI 使用。
5. **规格是 source of truth**：代码服务于规格，生成图用于校验偏移，不反过来让现状代码自动成为正确设计。

推荐总架构：

```text
Blueprint Core Engine
  ├─ Target / Intent extraction
  ├─ Blueprint Graph generation
  ├─ Actual Graph extraction
  ├─ Diff / Rules
  ├─ Evidence aggregation
  └─ Report / Query API

Phase 1 Consumers
  ├─ SDD CLI
  ├─ Agent review gate
  ├─ .sisyphus reports
  └─ Markdown/JSON report output

Phase 2 Consumers
  └─ TianYuan Blueprint Workbench
```

---

## 4. YAML 的最新定位

本设计不要求人工手写 Blueprint YAML。

人工和 Agent 应该编写的是：

- `docs/sdd/` 中的 Intent / 设计文档；
- `.sisyphus/plans/` 中的执行计划与任务卡；
- 必要时在文档或任务中补充结构化字段。

Blueprint Graph、Actual Graph、Evidence Graph 应由工具生成。

因此 YAML 如果存在，只能作为 **生成快照 / review artifact**，而不是手工维护的 source of truth。

推荐格式分工：

| 内容 | 格式 | 谁写 | 作用 |
|------|------|------|------|
| Intent / 目标 | Markdown / 结构化任务卡 | 人 + Agent | source of truth |
| Blueprint Graph | SQLite / JSON / generated YAML snapshot | 工具生成 | 目标实现结构 |
| Actual Graph | SQLite | AST / 代码抽取工具生成 | 当前代码现实 |
| Evidence | JSON / 日志 / 测试报告 / trace / 截图 | 工具生成 | 证明实现跑过 |
| Mismatch Report | JSON + Markdown | Diff 引擎生成 | 给 Agent / 人审查 |

如果生成 YAML，文件必须带有类似头部：

```yaml
# AUTO-GENERATED. DO NOT EDIT.
# Source: docs/sdd/... + .sisyphus/plans/... + extraction rules
```

YAML 的价值是：便于人类 review、git diff 和调试；不是要求人手维护配置。

---

## 5. 分层星云 / Blueprint 可视化方向

可视化方向是合理的，但它应建立在稳定图数据之上。

目标视图不是普通流程图，而是一个抽象进度地图：

```text
Feature / Intent 层
  ↓
界面元素层
  ↓
前端代码层
  ↓
IPC / API 层
  ↓
Rust 后端层
  ↓
DB / 存储层
  ↓
测试 / Evidence 层
```

节点状态示例：

| 状态 | 展示含义 |
|------|----------|
| 已规划但未实现 | ghost node |
| 已实现且匹配 | green |
| 缺失实现 | red |
| 计划外实现 | yellow / amber |
| 测试证据不足 | orange |
| 正在实现 | blue pulse / animated edge |
| evidence 通过 | green check |

视图的目标是回答：

> 一个功能从设计目标到 UI、前端、IPC、后端、DB、测试证据，通了几层？缺口在哪里？偏移在哪里？

第一阶段只输出报告和图数据 artifact；第二阶段再进入 TianYuan Workbench，形成可缩放、可点击、可跳转的 Blueprint Map。

---

## 6. 设计决策记录

| 决策点 | 选项 | 最终选择 | 原因 |
|--------|------|----------|------|
| Blueprint Graph 首要定位 | A) TianYuan 产品功能 / B) SDD-Agent 基础设施 | B，后续产品化为 A | 先解决 Agent 实现不跑偏的问题，再做 UI 工作台能力 |
| 阶段顺序 | A) 先做可视化 / B) 先做核心引擎 | B | 图数据不准时，可视化没有实际价值 |
| YAML 角色 | A) 人工维护合同 / B) 生成快照 / C) 不使用 | C（Phase 1）；B（Phase 2 可选） | Phase 1 不生成 YAML artifact，JSON 是唯一产出；Phase 2 可选生成 YAML 用于 debug/review |
| 图数据主存储 | A) YAML / B) JSON / C) SQLite | B（Phase 1），C（Phase 2） | Phase 1 使用 JSON 文件确保简单性和可追溯性；Phase 2 产品化时迁移到 SQLite 以支持查询和可视化读取 |
| 可视化布局 | A) 纯 force 星云 / B) 确定性分层图 + 星云视觉效果 | B | 分层布局便于检查进度，星云效果用于体验增强 |
| 产品化时机 | A) 立即进 TianYuan UI / B) SDD 工具稳定后再融合 | B | 防止偏离"先提升设计/执行能力"的初始目标 |
| v1.2 前端契约图边界 | A) 直接做产品可视化 / B) 只扩展 CLI 静态契约验证 | B | Fluid Workbench 需要先验证 View/Store/Component/Test 链路，但仍不进入产品 UI、SQLite 或运行时布局验证 |
| v1.2 前端节点 | A) 继续只支持 IPC / B) 增加 `TSModule`、`VueComponent`、`Store`、`Composable`、`ViewDefinition`、`LayoutProfile`、`Test` | B | 支撑 Workbench 的 View Registry、Layout Profile、Pinia 状态和测试证据规划 |
| v1.2 前端 diff | A) 复用 TS/Rust 双 actual diff / B) 单 actual graph 的 required node/edge 对齐规则 | B | 前端静态抽取是单一图，不需要强行模拟 IPC 的 TS/Rust 分裂 |

---

## SDD v1.1 Addendum

> **注意**：SDD v1.1 是政策/模板优先的规范，而非实现范围。v1.1 的核心内容已迁移至 [SDD v1.1 Graph-Verified Development](sdd-v1-1-graph-verified-development.md) 作为权威规范。

SDD v1.1 引入了 Graph-Verified Development 框架，将 Blueprint Graph 作为 SDD gate 的受管辖证据层。本文档（`blueprint-graph-architecture.md`）记录的是 Phase 1 架构基线，其中所有 Phase 1 范围锁定决策保持不变。

**v1.1 适用性判断**：
- **必须使用 v1.1**：跨层链路设计验证、Final Wave 验收、多 Agent 协作场景
- **仍用 Phase 1 基线**：单模块内实现、单次 commit 内的增量图更新

详见 [SDD v1.1 Graph-Verified Development](sdd-v1-1-graph-verified-development.md) 的 Graph-Verified Applicability 章节。

---

## SDD v1.2 Addendum：Frontend Contract Graph

> **定位**：v1.2 是 SDD 工具扩展，不是 TianYuan 产品 UI。它服务于 Fluid Workbench 后续实现前的结构验证。

Phase 1 的 Quest IPC 试点范围保持锁定。v1.2 在同一 `GraphArtifactV1` JSON artifact 上增加前端契约图能力，用于覆盖 Workbench 计划里 Phase 1-4 的静态结构链路：

```text
View opener / TS module
  → ViewDefinition
  → VueComponent
  → Store / Composable
  → Test evidence
  → LayoutProfile mutation
```

### 支持内容

| 类型 | 支持 |
|------|------|
| Vue SFC | `.vue` 文件作为 `VueComponent` 节点 |
| TS module | `.ts/.tsx` 文件作为 `TSModule` 节点 |
| Pinia | `defineStore(...)` 变量作为 `Store` 节点 |
| Composable | exported `useXxx` 函数/变量作为 `Composable` 节点 |
| View Registry | `registerView(...)` / `viewRegistry.register(...)` 生成 `ViewDefinition` 与 `REGISTERS_VIEW` |
| View opener | `openView(...)` / `focusView(...)` / `requestOpenView(...)` / `openWorkbenchView(...)` 生成 `OPENS_VIEW` |
| Layout profile | `setLayoutProfile(...)` / `activateLayoutProfile(...)` / `applyLayoutProfile(...)` 生成 `MUTATES_LAYOUT` |
| Test evidence | `__tests__`、`.test.ts`、`.spec.ts` 生成 `Test` 与 `TESTED_BY` |

### 不支持内容

- 不验证拖拽命中区域、布局几何、焦点顺序或视觉重叠；
- 不验证 runtime state 是否真实同步；
- 不验证 Playwright 截图、浏览器交互或 Tauri 多窗口生命周期；
- 不替代 Vitest、Playwright、人工 review 或 Final Wave。

### 工具入口

```bash
pnpm blueprint-graph:extract-frontend -- --pilot workbench --source <plan.md> --out <evidence-dir> --src src
pnpm blueprint-graph:check-frontend -- --pilot workbench --source <plan.md> --out <evidence-dir> --src src
```

v1.2 的审查含义是：目标计划中声明的前端契约节点和边，必须能从静态代码中抽取出来。没有抽到时生成 `BLUEPRINT_EXPECTED_GRAPH_MISSING_ACTUAL` finding，由审查者决定是否阻塞当前实现波次。

---

## 7. Phase 1 范围锁定（近期不做）

为避免偏离当前目标，Phase 1 明确排除以下范围：

**试点与范围**：
- 不扩展到 Quest 以外的 pilot（如 Chat、其他功能链）。
- 不实现 SQLite 存储；Phase 1 使用 JSON 文件。

**YAML 与配置**：
- 不要求人工手写 Blueprint YAML；Phase 1 不生成 YAML artifact。
- 不将 YAML 作为 source of truth 或配置合同。

**语义与 CI**：
- 不实现 CI 阻塞 gate；rule findings 仅出现在报告中。
- 不实现自定义 rule DSL 或 plugin 引擎。

**可视化与产品**：
- 不先实现完整 TianYuan 可视化工作台；
- 不先追求复杂动画、星云布局或 WebGL 大图；
- 不实现分层/nebula 查看器或 Blueprint Workbench。

**其他**：
- 不把现有代码反向生成结果直接视为正确设计；
- 不实现通用 evidence ingestion 数据库。

---

## 8. 下一步

在进入核心能力建设前，需要先确认本架构基调：

1. Blueprint Graph 首先作为 SDD / Agent 工具，而非产品 UI；
2. YAML 不作为人工入口，只作为可选生成快照；
3. 核心引擎先行，视觉层后置；
4. 未来产品化进 TianYuan Workbench，但不抢第一阶段优先级；
5. 生成图数据必须服务于 Agent 审查、计划执行、证据归档和后续可视化。

确认后，再开始拆解 Blueprint Core Engine 的核心能力设计与执行计划。
