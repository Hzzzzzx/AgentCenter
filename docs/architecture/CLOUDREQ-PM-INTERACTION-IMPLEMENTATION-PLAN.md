# CloudReq 项目管理交互改造实施方案

> 状态：Draft
> 日期：2026-05-13
> 目标：在不重构主干的前提下，完成 CloudReq 项目/空间/迭代交互闭环，让内部团队仅做增量实现且避免合并冲突。

## 1. 背景与目标

当前 AgentCenter 已具备 CloudReq 相关接口与基本 UI 能力，但存在参数语义不一致、返回结构过重、同步反馈不足、异常语义不清等问题，导致联调成本高、回归风险高。

本方案聚焦：

1. 固化前后端交互契约（只谈业务出入参与状态语义）。
2. 保持“保存选择”和“触发同步”解耦。
3. 完成三级联动、回显、失效降级与筛选一致性。
4. 用最小代码改动达到可上线的稳定体验。

---

## 2. 目标交互（最终用户感知）

1. 用户按“项目 -> 空间 -> 迭代”逐级选择。
2. 项目变化时清空空间/迭代；空间变化时清空迭代。
3. 点击“保存选择”仅保存上下文，不自动同步。
4. 点击“同步数据”才触发工作项同步。
5. 页面重进自动回显上次选择。
6. 若历史迭代已失效，自动降级为“项目+空间保留，迭代置空并提示重选”。
7. 任何接口调用都能明确区分：loading / empty / error / success。

---

## 3. 接口契约收敛（业务口径）

## 3.1 保持接口范围不变

- `GET /api/cloudreq-selection/projects`
- `GET /api/cloudreq-selection/spaces`
- `GET /api/cloudreq-selection/iterations`
- `POST /api/cloudreq-selection/select`
- `POST /api/project-data-providers/sync`
- `GET /api/project-data-providers`
- `GET /api/work-items`

## 3.2 入参规范

1. `projects`：无业务入参。
2. `spaces`：继续使用 `groupId`（不改成 projectId）。
3. `iterations`：将当前歧义参数名 `projectId` 改为 `spaceId`。
4. `select`：
   - 必填：`projectId`, `groupId`, `spaceId`
   - 建议必填：`projectName`, `spaceName`
   - 可空：`iterationId`, `iterationName`
5. `sync`：短期保持无入参（使用已保存选择）。
6. `work-items`：继续支持可选过滤 `projectId/spaceId/iterationId`。

## 3.3 出参规范

1. 列表接口统一要求“稳定 ID + 展示名称”（字段可保留历史名）。
2. `select` 建议返回“保存后的上下文对象 + updatedAt”，不返回整包快照。
3. `sync` 增加统计信息：`total/created/updated/skipped/failed`。
4. `work-items` 保证返回的 `projectId/spaceId/iterationId` 语义稳定，不混入名称。

---

## 4. 错误语义规范

统一错误结构（业务层）：

```json
{
  "code": "INVALID_PARAM | SCOPE_INVALID | SYNC_FAILED | UPSTREAM_EMPTY",
  "message": "...",
  "details": "..."
}
```

说明：
- `UPSTREAM_EMPTY` 表示上游返回空数据，不等于系统异常。
- `SYNC_FAILED` 必须返回可定位信息，避免前端误判为空数据。

---

## 5. 代码改造边界（按职责分工）

## 5.1 内部实现方（他）

1. 查询接口与数据获取逻辑。
2. 参数语义修正（`iterations` 的 `spaceId`）。
3. `select/sync/work-items` 出参与错误码收敛。
4. 数据结构增量改造（不破坏主干字段语义）。

## 5.2 你（总体逻辑方）

1. 前端三级联动和状态编排。
2. 保存/同步解耦交互闭环。
3. 回显与失效降级策略。
4. 列表与工作项筛选一致性策略。

---

## 6. 分阶段实施（两周节奏）

### P0（必须）

1. `iterations` 入参改名为 `spaceId`。
2. `sync` 返回统计字段。
3. 统一业务错误码。
4. 固化“保存不触发同步”。

### P1（高优先）

1. 页面重进回显稳定。
2. 失效迭代自动降级。
3. 前端建立 `{id, name}` 适配层，屏蔽后端字段差异。

### P2（后续优化）

1. `work-items` 分页参数。
2. `select` 返回体进一步标准化和版本化。

---

## 7. 验收标准

1. 用户在三级联动下可稳定完成选择、保存、同步。
2. 同步结果可见（含统计），失败可见（含错误码）。
3. 重进页面可回显；失效迭代可自动降级。
4. 工作项筛选与当前上下文一致，不出现 ID/名称混用引发的错筛。
5. 改动以增量方式完成，无需重构主流程。

---

## 8. 风险与回滚

1. 参数改名风险：前后端不同步导致 400。
   - 处理：短期兼容 `projectId` 与 `spaceId` 双读，发布后移除旧参数。
2. 返回结构改动风险：前端读取字段失败。
   - 处理：新增字段优先，不立即删除旧字段。
3. 错误码切换风险：旧错误处理逻辑失效。
   - 处理：前端先支持新旧两套错误结构。

