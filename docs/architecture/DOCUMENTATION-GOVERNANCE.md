# Documentation Governance

> 状态：Active
> 适用范围：AgentCenter 项目文档、SDD 计划、验证证据和归档材料。

本文件定义 AgentCenter 的文档生命周期和归档流程。`AGENTS.md` 只保留原则与入口；具体归档规则以本文为准。

---

## 1. 目标

文档治理解决三个问题：

1. **防止 source of truth 腐化**：过时文档不能继续影响实现和审查。
2. **让小改动也有轻量归档路径**：不是每个任务都写长文档，但每个任务都要判断是否影响文档。
3. **让协作者快速定位当前有效信息**：活跃文档、历史材料、执行证据分层存放。

---

## 2. 文档类型和目录

| 类型 | 目录 | 用途 | 是否默认进入上下文 |
|---|---|---|---|
| 项目协议 | `AGENTS.md` | SDD 原则、协作规则、验证入口 | 是 |
| 启动说明 | `README.md` | 依赖、端口、启动、故障排查 | 是 |
| 文档索引 | `docs/README.md` | 全项目文档导航和状态索引 | 是 |
| 架构文档 | `docs/architecture/` | 目标态、ADR、领域模型、Bridge/Runtime/Workflow 合同 | 是，按任务相关性读取 |
| 原型/UI 基线 | `docs/prototype/` | 高保真、主题、交互基线、截图入口 | UI 任务默认读取 |
| 调研材料 | `docs/research/` | 外部平台、竞品、参考模式 | 否，除非任务要求 |
| 历史归档 | `docs/archive/` 或子目录 `archive/` | 过时讨论稿、旧原型、历史材料 | 否 |
| 执行计划 | `.sisyphus/plans/` | L3+ 或跨模块任务的 PRD/HLD/LLD/Verification | 当前任务读取 |
| 验证证据 | `.sisyphus/evidence/` | 测试、curl、截图、运行输出摘要 | 审查时读取 |
| 过程笔记 | `.sisyphus/notepads/` | 调试经验、踩坑、临时 handoff | 否，需定期沉淀 |

`.sisyphus/` 只服务开发协作，不得被产品运行态读取。

---

## 3. 文档状态

| 状态 | 含义 | 允许动作 | 默认上下文 |
|---|---|---|---|
| `Draft` | 讨论中，尚未成为实现合同 | 可自由修改、合并、删除 | 否，除非任务引用 |
| `Active` | 当前有效 source of truth | 修改需同步索引和审查卡片 | 是 |
| `Implemented` | 已落地且仍有效 | 保留，可小幅维护 | 是，按任务相关性 |
| `Superseded` | 被新文档替代 | 文件头标注替代文档，索引降权或移动归档 | 否 |
| `Archived` | 历史材料，仅追溯 | 不再参与默认实现判断 | 否 |
| `Stale` | 疑似过时但未完成确认 | 标注原因，创建清理项 | 否，除非清理任务 |
| `Rejected` | 方案已否决 | 归档并注明原因 | 否 |

建议每个长期文档在文件头写：

```markdown
> 状态：Active
> 最近更新：YYYY-MM-DD
> Owner：architecture / prototype / runtime / workflow / research
> Supersedes：path or n/a
> Superseded by：path or n/a
```

旧文档没有这些字段时，不要求一次性补齐；触碰该文档时顺手补。

---

## 4. 分级归档规则

| 任务级别 | 归档要求 |
|---|---|
| L0 | 通常无需文档归档；审查卡片写 `docs: n/a` |
| L1 | 若修复已知陷阱或用户可见行为，更新相关 README/architecture/notepad；否则 `docs: n/a` |
| L2 | 判断是否影响用户行为、API、DTO、UI 文案/入口；有影响则更新对应长期文档或索引 |
| L3 | 必须执行 Documentation Impact Check；如有跨层合同变化，同步 `docs/architecture/` 和 evidence |
| L4 | 必须有归档计划；完成后更新索引、归档旧材料、沉淀新目标态或 ADR |
| 重构 | 若外部合同不变，归档 Expected Contract Changes 和验证证据；若文档中的文件/类/流程过时，标注 Superseded/Stale |
| 紧急 | 可先跳过完整归档；24h 内补原因、验证、文档同步或 stale 标注 |

---

## 5. Documentation Impact Check

每个任务完成前问一次：

| 问题 | 如果是 |
|---|---|
| 用户可见行为变了吗？ | 更新 README、prototype 或相关架构说明 |
| REST API / DTO / SSE payload 变了吗？ | 更新 `docs/architecture/` 中对应合同，补 curl/evidence |
| 数据库表、Flyway migration、状态枚举变了吗？ | 更新领域模型、验证说明和测试数据说明 |
| Workflow / Confirmation / Work Item 状态机变了吗？ | 更新领域模型或 workflow 目标态文档 |
| Runtime Adapter / OpenCode 进程管理变了吗？ | 更新 Bridge/Runtime 文档和已知陷阱 |
| UI 结构、导航、主题、原型基线变了吗？ | 更新 `docs/prototype/`，旧基线归档 |
| 架构边界、技术选型、长期决策变了吗？ | 新增或更新 ADR，索引到 `docs/architecture/README.md` |
| 发现旧文档明显过时了吗？ | 标注 `Stale` 或 `Superseded`，必要时移入 archive |

如果全部为否，在审查卡片写明：

```json
"docs": {"impact": "n/a", "reason": "no behavior, contract, UI, or architecture change"}
```

---

## 6. 任务完成归档流程

1. **Review Diff**：列出本次修改影响的模块、接口、状态、UI 和运行链路。
2. **Impact Check**：按第 5 节判断是否需要同步文档。
3. **Sync Active Docs**：更新当前有效文档，不在旧文档里继续叠加冲突信息。
4. **Archive or Mark Old Docs**：被替代的文档标注 `Superseded by`，必要时移入 `archive/`。
5. **Write Evidence**：验证材料放 `.sisyphus/evidence/{task}/`，不要塞进长期文档正文。
6. **Review Card**：记录 docs impact、updated、archived、stale-found。

审查卡片建议字段：

```json
{
  "docs": {
    "impact": "updated/n/a/stale-found",
    "updated": ["docs/architecture/example.md"],
    "archived": ["docs/archive/example.md"],
    "stale": ["docs/architecture/old.md"],
    "reason": "why this is enough"
  }
}
```

---

## 7. 归档位置

| 来源 | 归档位置 |
|---|---|
| `docs/architecture/*.md` | `docs/architecture/archive/{topic-or-date}/` 或 `docs/archive/architecture/` |
| `docs/prototype/*` | `docs/prototype/archive/{name-date}/` |
| `docs/research/*` | 保留原目录，索引状态改为 Reference/Archived；大型旧材料可进 `docs/archive/research/` |
| `.sisyphus/plans/*` | `.sisyphus/archive/{date-or-task}/` |
| `.sisyphus/evidence/*` | 保持 append-only，不主动移动；索引由计划或审查卡片引用 |
| `.sisyphus/notepads/*` | 沉淀后移入 `.sisyphus/archive/notepads/` 或保留并标注已沉淀 |

移动文档时必须保留可追溯链接：旧文件头或索引中注明新位置/替代文档。

---

## 8. 功能文档划分

| 功能域 | 长期文档位置 | 执行/证据位置 |
|---|---|---|
| Bridge API、SSE、Runtime Adapter | `docs/architecture/` | `.sisyphus/plans/` + `.sisyphus/evidence/` |
| Workflow、Confirmation、Work Item | `docs/architecture/` | `.sisyphus/plans/` + DB/API evidence |
| 前端工作台、会话 UI、看板 | `docs/architecture/` + `docs/prototype/` | `.sisyphus/evidence/ui-*` |
| 主题、首页、原型 | `docs/prototype/` | 截图、Playwright evidence |
| 外部平台和竞品 | `docs/research/` | 采纳时回写 ADR 或 architecture |
| 启动、环境、端口 | `README.md` + runtime/runbook 文档 | 启动日志或 smoke evidence |

---

## 9. 周期性文档巡检

建议在以下时机做轻量巡检：

- L4 完成后。
- 新增或替换核心架构文档后。
- 大批量原型/调研进入仓库后。
- 每次 release 或阶段性 handoff 前。

巡检清单：

1. `docs/README.md` 是否索引了活跃文档。
2. `docs/architecture/README.md` 阅读顺序是否仍合理。
3. 标记 Active 的文档是否引用了不存在的文件、类、接口。
4. 已归档文档是否仍被 AGENTS/README/活跃架构文档引用。
5. `.sisyphus/plans/` 是否堆积已完成计划。
6. 原型 archive 是否有说明和替代基线。

巡检发现问题不要求一次修完，但必须标注 `Stale` 或创建清理任务，避免继续误导后续实现。
