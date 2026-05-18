# TianYuan 文档规范

> 本规范定义项目文档的分类、存放位置、命名规则和生命周期管理。
> 所有贡献者（人类和 AI Agent）必须遵守。

---

## 一、文档分类体系

### 1.1 文档类型定义

| 类型 | 用途 | 存放位置 | 状态标记 |
|------|------|----------|----------|
| **设计文档** | 模块/系统的架构设计和决策记录 | `docs/{module}/` | `设计中 / 已实现 / 已归档` |
| **执行计划** | 针对特定目标的实施步骤 | `docs/plans/` | `Draft / 执行中 / 已完成` |
| **契约文档** | 前后端接口、事件、数据格式约定 | `docs/{module}/` 或 `docs/reference/` | `Frozen / 可变更` |
| **参考资料** | 外部项目分析、工具研究 | `docs/reference/` | 无状态 |
| **原型资产** | 高保真 HTML 原型 | `docs/assets/prototypes/` | 日期标签 |
| **截图资产** | QA 截图、设计参考图 | `docs/assets/{category}/` | 日期标签 |

### 1.2 目录结构

```
docs/
├── index.md                     # 总索引（必须维护）
├── CONTRIBULING.md              # 本规范
│
├── {module}/                    # 模块设计文档
│   ├── {feature}-design.md      # 功能设计（含决策记录表）
│   └── {feature}-contract.md    # 接口契约（如有）
│
├── plans/                       # 执行计划
│   └── {date}-{name}.md         # 仅保留活跃和待执行的计划
│
├── reference/                   # 参考资料
│   └── {topic}-analysis.md
│
├── assets/                      # 静态资产
│   ├── prototypes/              # 高保真原型
│   │   ├── {date}-{name}.html   # 活跃原型
│   │   └── archive-{date}/      # 归档原型
│   ├── {module}/                # 模块相关截图
│   └── qa-screenshots/          # QA 截图（gitignore）
│
├── history/                     # 已归档文档
│   ├── {date}-{name}.md
│   └── {topic}/                 # 批量归档
│
└── qoder-mechanics/             # 外部研究（特殊目录）
```

---

## 二、命名规则

### 2.1 文件命名

| 类型 | 格式 | 示例 |
|------|------|------|
| 设计文档 | `{module}/{feature}-design.md` | `editor/git-blame-gutter-design.md` |
| 执行计划 | `{date}-{name}.md` | `2026-04-24-expert-closure-plan.md` |
| 契约文档 | `{module}/{feature}-contract.md` | `novel/autopilot-event-contract.md` |
| 参考分析 | `{topic}-analysis.md` | `reference-projects-analysis.md` |
| 原型 | `{date}-{name}.html` | `2026-04-24-plotpilot-prototype.html` |

**日期格式**：`YYYY-MM-DD`，取计划创建日期。

### 2.2 模块名映射

| 模块目录 | 覆盖范围 |
|----------|----------|
| `novel/` | 小说模式、PlotPilot parity |
| `expert/` | Expert 模式、团队协作 |
| `editor/` | 编辑器、Monaco 集成 |
| `sidebar/` | 侧边栏、文件浏览器 |
| `memory-system/` | 记忆系统 |
| `code-review/` | 代码审查记录 |
| `chat/` | 对话系统（如需独立） |

---

## 三、文档生命周期

### 3.1 核心原则：不同文档类型有不同的生命周期

设计文档和执行计划的生命周期不同，不能用同一套状态。

### 3.2 执行计划的生命周期

执行计划（`docs/plans/` 中的文件）有明确的目标和验收条件。

```
Draft ──→ Approved ──→ Executing ──→ Completed ──→ Archived
  │           │            │
  │           │            ├─→ Blocked（等待依赖/问题）
  │           │            └─→ Revising（执行中发现方案需要修改）
  │           │
  │           └─→ Cancelled（审批不通过/优先级变化）
  │
  └─→ Superseded（被新计划取代，在文件头注明新计划路径）
```

**状态定义**：

| 状态 | 含义 | 触发条件 | 可执行操作 |
|------|------|----------|-----------|
| `Draft` | 正在写计划，方案还没定 | 新建计划文件 | 自由修改、讨论 |
| `Approved` | 方案已确认，可以开始执行 | 用户或架构师确认 | 不可改范围，可补充细节 |
| `Executing` | 正在按计划写代码 | 第一个 Phase 开始实施 | 更新 Phase 进度、追加防御性记录 |
| `Blocked` | 执行卡住了 | 遇到无法绕过的阻塞 | 记录阻塞原因和依赖，等待解除后回到 Executing |
| `Revising` | 执行中发现原方案有问题 | 实施过程中发现设计偏差 | 修订方案后重新进入 Executing |
| `Completed` | 所有 Phase 已完成并验证 | 验收条件全部满足 | **立即归档**到 history/ |
| `Cancelled` | 不做了 | 优先级变化或方案废弃 | 移入 history/，注明取消原因 |
| `Superseded` | 被新计划取代 | 出了覆盖范围更完整的计划 | 移入 history/，文件头注明新计划路径 |
| `Deferred` | 有价值但当前不执行 | 用户明确搁置 | 保留在 plans/，可在未来重新激活 |

**关键约束**：

- `Draft` 和 `Approved` 的区别：Draft 可以随便改范围，Approved 不能改范围只能补细节
- `Executing` 的计划**必须**在文件中记录每个 Phase 的完成状态（✅ / 🔄 / ⬜）
- `Completed` 的计划**不能留在 plans/**，必须归档

### 3.3 设计文档的生命周期

设计文档（`docs/{module}/` 中的文件）描述模块架构和决策，和代码长期共存。

```
Draft ──→ Confirmed ──→ Implemented ──→ Archived
  │           │              │
  │           │              └─→ Evolving（持续迭代，如核心模块）
  │           └─→ Rejected（方案否决）
  │
  └─→ 同上
```

**状态定义**：

| 状态 | 含义 | 特征 |
|------|------|------|
| `Draft` | 设计中，方案未收敛 | 决策表为空或不完整 |
| `Confirmed` | 方案已确认，待实现 | 决策表完整，所有关键决策已记录 |
| `Implemented` | 功能已实现 | 代码和文档一致，决策表最终状态 |
| `Evolving` | 已实现但持续迭代 | 核心模块（如 expert/novel）长期处于此状态 |
| `Archived` | 不再维护 | 移入 history/ |
| `Frozen` | 契约冻结 | 仅适用于契约文档，不可单方面变更 |

**关键区别**：设计文档和计划文档不同——设计文档 `Implemented` 后仍然留在 `docs/{module}/`，不需要移走。它长期作为模块的决策 source of truth。只有当模块被完全废弃时才归档。

### 3.4 执行中的计划如何记录进度

计划文件内部**必须**用标记追踪进度：

```markdown
### Phase A：后端基础设施 ✅
- [x] Step 1: 数据库 schema 变更
- [x] Step 2: Rust 命令实现
- [x] Step 3: 单元测试

### Phase B：前端对接 🔄
- [x] Step 1: Store 层 hydrate 逻辑
- [ ] Step 2: 组件 data-action-id 绑定
- [ ] Step 3: 集成测试

### Phase C：验收 ⬜
- [ ] Step 1: pnpm typecheck 通过
- [ ] Step 2: cargo test 通过
```

**规则**：
- `✅` = Phase 全部完成
- `🔄` = Phase 正在进行（一个会话中只能有一个 🔄）
- `⬜` = Phase 未开始
- `- [x]` / `- [ ]` = Step 级别追踪

### 3.5 归档触发条件

满足以下任一条件，**必须在当次会话结束前归档**：

| 条件 | 归档目标状态 | 操作 |
|------|-------------|------|
| 计划所有 Phase ✅ | `Completed` | 移入 history/ |
| 计划被新计划取代 | `Superseded` | 移入 history/，文件头注明新计划 |
| 用户明确取消 | `Cancelled` | 移入 history/，注明原因 |
| 超过 30 天无更新且无人认领 | `Deferred` | 确认后移入 history/ 或保留 |
| 设计文档对应的模块被废弃 | `Archived` | 移入 history/ |

**注意**：`Deferred` 状态的计划保留在 plans/ 中，其余归档状态都移入 history/。

### 3.6 归档操作

```bash
# 单文件归档
git mv docs/plans/{file} docs/history/{file}

# 批量归档（同一主题）
mkdir -p docs/history/{topic}
git mv docs/plans/{related-files} docs/history/{topic}/
```

归档后在原位置不留副本。

---

## 四、设计文档模板

每个功能模块**必须**有一个设计文档，结构如下：

```markdown
# {功能名} 设计

> 状态：设计中 / 已实现 / 已归档
> 最后更新：YYYY-MM-DD

## 概述
（一段话描述功能和目标）

## 交互设计
（用户如何使用这个功能，关键交互路径）

## 技术实现
（架构、关键文件、核心逻辑、依赖关系）

## 防御性编程
（发现的坑、null guard、特殊处理、已知限制）

## 设计决策记录

| 决策点 | 选项 | 最终选择 | 原因 |
|--------|------|----------|------|
| （遇到时追加） | A) ... / B) ... | A | ... |
```

**决策记录规则**：
- 每次会话中做出的技术决策，必须追加到决策表
- 不要修改已有决策（除非用户明确要求推翻）
- 这是防止决策丢失在会话上下文中的核心机制

---

## 五、执行计划模板

```markdown
# {标题}

> 状态：Draft / 执行中
> 创建日期：YYYY-MM-DD
> 适用范围：简要描述边界
> 前置基线：依赖的设计文档或 commit

## 目标
（完成标准，可量化的验收条件）

## 当前基线
（已有代码/能力的盘点）

## 分阶段计划

### Phase A：{名称}
- 目标：...
- 文件范围：...
- 验收条件：...

## 依赖关系
（与其他计划的先后关系）
```

---

## 六、docs/plans/ 维护规则

### 6.1 进入 plans/ 的条件

只有满足以下条件的文档才能放入 `docs/plans/`：

1. 状态为 `Draft` 或 `执行中` 或 `设计中`
2. 近期有被引用或更新
3. 尚未完全实现的计划

### 6.2 离开 plans/ 的条件

- 已完成 → 移入 `docs/history/`
- 被新计划取代 → 在新计划中注明取代关系，旧计划移入 `docs/history/`
- 契约类 → 移入 `docs/reference/` 或对应模块目录

### 6.3 定期清理

每个自然周开始时，检查 `docs/plans/`：
1. 超过 14 天未更新且状态为 Draft → 确认是否继续
2. 所有 Phase 已完成 → 归档
3. 与代码现状严重不符 → 更新或归档

---

## 七、根目录清洁规则

| 内容 | 应放位置 | 禁止 |
|------|----------|------|
| QA 截图 | `docs/assets/qa-screenshots/` | 散落在根目录 |
| 原型 HTML | `docs/assets/prototypes/` | 散落在根目录 |
| Obsidian 配置 | `.gitignore` 排除 | 入库 |
| Graphify 产物 | `.gitignore` 排除 | 入库 |
| 实验文件 | 删除或归档到 `docs/history/` | 保留 pet.html 等 |

---

## 八、Agent 协作规范

AI Agent 在操作文档时必须遵守：

1. **开工前**：读取相关设计文档的决策记录，不重复讨论已定方案
2. **完工后**：将新决策追加到设计文档的决策表
3. **会话收尾**：确认所有修改过的功能文档已同步，更新 `docs/index.md`
4. **不创建空文档**：只有当内容实质性存在时才创建文件
5. **归档不删除**：移入 `history/` 而非 `rm`，保留历史可追溯

---

## 更新日志

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-04-25 | v1.0 | 初始版本 |
