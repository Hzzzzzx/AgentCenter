# CloudReq 集成顶层调整方案（增量优先 / 低冲突）

> 状态：proposal
> 最近更新：2026-05-13
> 目标：让 AgentCenter 内部主干保持稳定，CloudReq 适配仅以增量代码演进，并降低后续合并冲突概率。

## 1. 一句话结论

将 CloudReq 集成从“侵入既有字段语义”改为“外部 ID 映射层 + Provider 能力契约 + 可回退读路径”：

- 内部主模型继续使用稳定的内部 ID（ULID）作为主关联键。
- 所有外部系统（CloudReq）ID 进入统一映射层，不直接挤占内部核心字段语义。
- 前后端 scope 过滤通过“标准 ScopeKey（providerId + internalId + externalRef）”兼容读取，避免一次性迁移导致冲突。

---

## 2. 当前冲突根因（为何容易反复打架）

1. **字段语义混用**：同一个 `work_item.project_id` 同时承载“内部项目 ULID”和“外部 CloudReq projectId”，导致过滤、回显、关联行为互相覆盖。
2. **写路径耦合**：`select -> sync -> setActiveScope` 串在一个流程，任何一段做默认补偿都可能覆盖用户刚刚选中的外部上下文。
3. **前端筛选耦合单一 ID**：UI 下拉用外部 ID，列表过滤用内部字段，导致只能通过临时禁用 scope 规避。
4. **mock/fallback 位置不清**：Provider 层、Adapter 层和 UI 层都可能做“兜底”，线上容易出现假数据污染。

---

## 3. 顶层设计原则（建议作为后续合并准则）

### P1. 主域稳定原则
- `work_item` 的主关联键保持内部稳定语义，不因外部系统变化而重定义。
- 外部系统标识通过“扩展映射”表达，不反向污染主域字段。

### P2. Provider 适配器分层原则
- Adapter：只负责调用 CloudReq API + 失败语义标准化。
- Provider：只负责把 CloudReq 投影为 AgentCenter 标准快照。
- Sync Service：只处理“标准快照 -> 本地实体”落库，不感知 CloudReq 特例分支。

### P3. 选择与同步解耦原则
- 用户“选择上下文”是配置写操作。
- “执行同步”是数据拉取写操作。
- 两者分事务、分入口、分幂等键；避免选择动作触发的同步副作用覆盖配置。

### P4. 兼容优先原则
- 读路径先兼容，写路径再收敛。
- 先做到“不破坏既有项目 / provider”，再逐步收敛 CloudReq 语义。

---

## 4. 推荐落地方案（选型建议）

基于你给出的 A/B/C，推荐 **C+（C 的增强版）**：

1. 保留现有内部字段（`project_id/space_id/iteration_id`）用于内部关联。
2. 新增外部映射字段（或独立映射表）承载：
   - `external_provider_id`（如 `cloudreq-real`）
   - `external_project_id`
   - `external_space_id`
   - `external_iteration_id`
3. 给 `provider + external_project_id + external_space_id + external_iteration_id` 建唯一约束（或软唯一）。
4. 前端 scope 过滤改为优先传 `providerId + external*`，后端统一翻译为查询条件。

这样做的好处：
- 不需要立即迁移所有历史 ULID 数据。
- CloudReq 合并只新增字段/表和映射逻辑，冲突最小。
- 后续再接入 Jira/Azure DevOps/禅道也复用同一套外部映射框架。

---

## 5. 合并分层建议（内部主干最小扰动）

### 层 1：可直接合并（低风险纯增量）
- CloudReq 选择 Controller / DTO / Service（只要不直接改动核心同步流程）。
- Adapter 增加必需 header（如 `agencyid`）与错误语义标准化。

### 层 2：受控合并（中风险，需要 feature flag）
- 自动选择项目逻辑。
- `sync()` 避免覆盖用户选择逻辑。

建议以开关控制：
- `feature.cloudreq.selection.autofallback.enabled`
- `feature.cloudreq.sync.protect-selection.enabled`

### 层 3：暂缓合并（高冲突，需要先统一契约）
- 直接改写 `work_item.project_id/space_id/iteration_id` 语义。
- 前端全局禁用 scope 过滤（只能作为短期 debug 开关）。

---

## 6. 前后端契约调整（避免下次再改同一批文件）

1. `GET /project-context` 返回双轨字段：
   - `internalContextId` / `internalSpaceId` / `internalIterationId`
   - `providerId` / `externalProjectId` / `externalSpaceId` / `externalIterationId`
2. `POST /cloudreq-selection/select` 仅做“选择保存”，返回 `selectionVersion`。
3. `POST /project-data/sync` 显式触发同步，入参带 `selectionVersion`（防止旧页面覆盖新选择）。
4. `GET /work-items` 接口支持：
   - `scopeMode=internal|external|auto`
   - 默认 `auto`：先 external，缺失则回落 internal。

---

## 7. 数据迁移策略（不一次性大迁移）

### 阶段 M1（本周可落地）
- 新增外部映射字段/表。
- 写入新数据时双写（internal + external mapping）。
- 查询走 auto 兼容模式。

### 阶段 M2（稳定后）
- 为历史数据补齐 external mapping（仅 CloudReq 关联范围）。
- 监控 scope miss 率、空列表率、选中回显失败率。

### 阶段 M3（可选）
- 若多 Provider 规模扩大，再考虑把 external mapping 抽成独立聚合服务。

---

## 8. 冲突预防机制（流程层）

1. **变更切面约束**：
   - CloudReq 团队默认只改 `provider/cloudreq/**`、`selection/**`、`mapping/**`。
   - 改 `ProjectDataSyncService` 需架构 owner 审阅。
2. **契约测试门禁**：
   - 增加 provider contract tests（项目/空间/迭代/回显/同步）
   - 增加 scope 过滤双轨测试（internal/external/auto）
3. **Feature Flag 灰度**：
   - 测试环境先开 `external scope mode`。
   - 生产先只开“回显不覆盖”，再开“external filter”。

---

## 9. 对你这批改动的吸收建议（对应你文档）

- `CloudReqScopeSelection*` 新增文件：可优先吸收。
- `CloudReqAdapter` 的 `agencyid` 与去 mock：建议吸收，但 mock 迁移到 dev profile 专用实现。
- `ProjectDataSyncService` 外部 ID 写主字段：不要直接全量吸收，先改为写 external mapping。
- `App.vue` 禁用 scope：不要常驻主干，改成调试开关并尽快恢复 auto 过滤。
- `ProjectContextSettings.vue` 初始化回显：应保留，并改成双轨 ID 回显策略。

---

## 10. 验收标准（合并完成后的“完成定义”）

1. 用户重新进入页面后，项目/空间/迭代下拉可稳定回显（不丢选中）。
2. scope 过滤在 internal/external 混合数据下不为空、不串项。
3. sync 不会覆盖用户最新 selection（并发刷新也不覆盖）。
4. CloudReq API 失败时不返回误导性 mock 数据。
5. 不引入对 `.sisyphus/` 的运行时依赖。

---

## 11. 决策建议（供架构评审拍板）

- **首选**：C+（新增 external mapping，保留内部主键语义）
- **不建议立即采用**：A（全量外部 ID 统一），迁移成本和冲突风险过高
- **可短期过渡**：B（双格式并存），但必须尽快收敛到“字段语义明确”的 C+

---


## 12. 需要企业内部澄清的对接问题（请逐项确认）

为保证“外部做顶层/接口设计，内部只做增量实现且不冲突”，建议在合并前由企业内部给出明确答案：

1. **主键语义边界**：`work_item.project_id/space_id/iteration_id` 是否被其他内部系统（报表、权限、审计、导出）硬依赖为 ULID？
2. **外部 ID 稳定性**：CloudReq 的 `projectId/spaceId/iterationId` 是否全生命周期稳定，是否存在重建后 ID 变化场景？
3. **跨 Provider 并存策略**：同一项目是否允许同时关联多个 provider（CloudReq/Jira 等）？若允许，active scope 冲突如何裁决？
4. **选择与同步时序**：是否接受 `select` 与 `sync` 分离为两个 API，并以 `selectionVersion` 做并发保护？
5. **历史数据兼容要求**：现网历史数据是否必须“零迁移可用”？若必须，兼容窗口需要多长（如 3 个月/6 个月）？
6. **scope 过滤优先级**：筛选命中冲突时，是否统一按 `external > internal > all` 回退，还是必须强一致只返回精确命中？
7. **失败语义与 UX**：CloudReq API 失败时是否明确要求“空列表 + 可观测错误”，并禁止任何 mock 回填？
8. **配置权限模型**：谁可以修改项目/空间/迭代选择（个人级、项目级、租户级）？是否需要审批或审计留痕？
9. **回填策略**：external mapping 是否要求双写 + 异步回填，还是允许先只写新数据、历史按需补齐？
10. **验收口径**：合并验收是以“无冲突 + 回显稳定 + 不覆盖选择”为主，还是还要求“全量历史筛选一致”？

### 建议确认输出模板（供企业内部回填）

```json
{
  "id_semantics": "internal_ulid_locked | can_change",
  "external_id_stability": "stable | may_change",
  "multi_provider": "allowed | forbidden",
  "select_sync_mode": "decoupled_with_version | coupled",
  "history_compat_window": "3m | 6m | none",
  "scope_fallback": "external_internal_all | strict_exact",
  "api_failure_policy": "empty_plus_error | fallback_mock",
  "permission_scope": "user | project | tenant",
  "backfill_mode": "dual_write_async_backfill | new_only",
  "acceptance_priority": "no_conflict_first | history_consistency_first"
}
```

> 只要以上 10 项结论明确，内部实现即可收敛为映射层、查询兼容层和少量 UI 回显增量改造，避免反复修改核心同步链路。

---

