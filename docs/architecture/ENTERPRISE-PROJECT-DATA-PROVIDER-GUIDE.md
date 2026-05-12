# 企业项目数据 Provider 接入指南

> 适用版本：本次整改后的 Project Data Provider 版本。目标是企业内部只实现一个后端 Provider，不改前端 mock、不改 AgentCenter Web，即可切换到企业内部项目、空间、迭代、FE/US/TASK/WORK/BUG/VULN 和项目级任务编排数据。

## 和上一版的差异

上一版适配容易出问题的点，是前端仍保留项目/迭代 mock，工作项查询没有带 provider 维度，切换实现位后同名项目或同名 Sprint 可能仍看到旧数据。本版规则调整如下：

| 整改点 | 新规则 |
|--------|--------|
| 项目/空间/迭代选项 | 只能来自 Bridge 的 `/api/project-data-providers/sync` 或 `/snapshot` |
| 前端 mock | 不允许作为运行数据源；本地测试数据也必须由后端 fixture provider 返回 |
| 工作项查询 | 必须带 `providerId + projectId + spaceId + iterationId` 过滤；其中筛选键来自稳定外部 id，不使用展示名 |
| 同步审计 | 每次 sync 写入 `project_data_sync_history` |
| 任务编排 | `workflow_definition` 增加 `project_id`，按当前项目加载/保存，启动工作流按工作项项目优先匹配 |
| 扩展字段 | 使用 `extraJson`，不直接扩 AgentCenter 公共表结构 |

## 后端实现位

企业内部实现一个 Spring Bean 即可：

```java
@Component
public class EnterpriseCloudeReqProjectDataProvider implements ProjectDataProvider {
    @Override
    public String id() {
        return "enterprise-cloudereq";
    }

    @Override
    public String name() {
        return "企业 CloudeReq";
    }

    @Override
    public String description() {
        return "企业内部项目、空间、迭代和事项同步。";
    }

    @Override
    public ProjectDataSnapshotDto snapshot() {
        // 调企业内部接口，并映射成 ProjectDataSnapshotDto。
    }
}
```

Provider 会被 `ProjectDataProviderRegistry` 自动注册。运行设置页切换到 `enterprise-cloudereq` 后，前端会触发后端 sync，并刷新项目管理页、标题栏迭代下拉、首页统计、工作项列表和任务编排配置。

## Provider 必须返回的数据

`snapshot()` 返回一个完整快照：

```java
new ProjectDataSnapshotDto(
    "enterprise-cloudereq",
    contexts,
    options,
    workItems,
    OffsetDateTime.now(ZoneOffset.UTC)
);
```

必须保证 `ProjectDataSnapshotDto.providerId` 等于 `ProjectDataProvider.id()`，否则 Bridge 会拒绝同步并记录失败历史。

## 建议内部接口能力

企业内部接口可以自由设计，但 Provider 至少要拿到这些逻辑数据：

| 能力 | 必需字段 |
|------|----------|
| 项目 | 稳定项目 id、展示名、可选 CloudeReq 项目 id/name |
| 空间 | 稳定空间 id、展示名、所属项目 id |
| 迭代 | 稳定迭代 id、展示名、所属项目/空间 id、状态、起止时间 |
| 工作项 | 稳定工作项 id、code、类型、标题、描述、状态、优先级、项目 id、空间 id、迭代 id、处理人 |

可参考的内部 REST 形态：

| Method | Path | 用途 |
|--------|------|------|
| `GET` | `/projects` | 项目列表 |
| `GET` | `/projects/{projectId}/spaces` | 项目下空间 |
| `GET` | `/projects/{projectId}/spaces/{spaceId}/iterations` | 空间下迭代 |
| `GET` | `/work-items?projectId=&spaceId=&iterationId=&updatedAfter=` | FE/US/TASK/WORK/BUG/VULN 列表或增量 |

内部接口不需要和上表完全一致，只有 Provider 到 AgentCenter 的 DTO 契约必须稳定。

## DTO 映射

`ProjectContextDto` 对应“一个可生效上下文”，也就是项目 + CloudeReq 项目 + 空间 + 迭代：

| AgentCenter 字段 | 企业来源 |
|------------------|----------|
| `id` | Provider 内稳定上下文 id，例如 `ctx-{projectId}-{spaceId}-{iterationId}` |
| `externalProjectId` | 企业稳定项目 id |
| `project` | 标题栏展示的项目名，不作为隔离键 |
| `externalCloudeReqProjectId` | CloudeReq 项目 id |
| `cloudeReqProject` | CloudeReq 项目展示名 |
| `externalSpaceId` | 企业稳定空间 id |
| `space` | 空间展示名 |
| `externalIterationId` | 企业稳定迭代 id |
| `iteration` | 迭代展示名 |
| `iterationStatus` | 迭代状态，可空 |
| `iterationStartAt` / `iterationEndAt` | 起止时间，可空 |
| `active` | Provider 默认生效上下文，只允许一个优先为 true |
| `extraJson` | 企业扩展字段 |

前端和 Bridge 使用稳定 scope key 做隔离：

| 查询参数 / 表字段 | 取值规则 |
|-------------------|----------|
| `providerId` | `ProjectDataProvider.id()` |
| `projectId` / `work_item.project_id` / `workflow_definition.project_id` | `${providerId}:${externalProjectId}` |
| `spaceId` / `work_item.space_id` | `externalSpaceId` |
| `iterationId` / `work_item.iteration_id` | `externalIterationId` |

展示名只用于 UI 展示。企业内部如果项目名、空间名或 Sprint 名重复，只要 external id 稳定且唯一，就不会串数据。

`ProjectProviderWorkItemDto` 对应 FE/US/TASK/WORK/BUG/VULN：

| AgentCenter 字段 | 企业来源 |
|------------------|----------|
| `externalId` | 企业稳定工作项 id，强烈建议必填 |
| `code` | 展示 code，同一 AgentCenter DB 内避免跨 provider 冲突 |
| `type` | `FE`、`US`、`TASK`、`WORK`、`BUG`、`VULN` |
| `title` / `description` | 标题和描述 |
| `status` | 映射到 `BACKLOG`、`TODO`、`IN_PROGRESS`、`IN_REVIEW`、`DONE` |
| `priority` | 映射到 `LOW`、`MEDIUM`、`HIGH`、`URGENT` |
| `project` / `space` / `iteration` | 展示名，必须和 context 可匹配 |
| `projectContextId` | 可选，指向 `ProjectContextDto.id` |
| `externalProjectId` / `externalSpaceId` / `externalIterationId` | 企业稳定 id |
| `assigneeUserId` | AgentCenter 用户 id，无法映射可空 |
| `extraJson` | 企业扩展字段 |

## 表结构和写入行为

同步会写入这些表：

| 表 | 用途 | 通用性 |
|----|------|--------|
| `project_provider_setting` | 全局当前 provider 和当前上下文 | 通用 |
| `project_context` | 项目/CloudeReq 项目 | 通用 |
| `project_space` | 空间 | 通用 |
| `project_iteration` | 迭代 | 通用 |
| `work_item` | FE/US/TASK/WORK/BUG/VULN，新增 provider/scope 字段 | 通用 |
| `project_data_sync_history` | 同步历史、成功/失败、数量和 active scope | 通用 |
| `workflow_definition` | 任务编排定义，新增 `project_id` | 通用 |

工作项 upsert key 是 `(provider_id, external_work_item_id)`。历史遗留无 provider 的行可以按 `code` 接管；已经属于其他 provider 的同 code 行会拒绝同步，避免串数据。

## 前端会调用的端点

| Method | Endpoint | 说明 |
|--------|----------|------|
| `GET` | `/api/project-data-providers` | Provider 列表、当前 provider、当前生效 scope |
| `PUT` | `/api/project-data-providers/active` | 切换全局 provider |
| `GET` | `/api/project-data-providers/snapshot` | 只读快照，不写库 |
| `POST` | `/api/project-data-providers/sync` | 拉取 provider 快照并写库 |
| `GET` | `/api/project-data-providers/sync-history?providerId=&limit=` | 同步历史 |
| `GET` | `/api/work-items?providerId=&projectId=&spaceId=&iterationId=` | 当前上下文工作项 |
| `GET` | `/api/work-items/overview?providerId=&projectId=&spaceId=&iterationId=` | 当前上下文首页统计 |
| `GET` | `/api/workflow-definitions?projectId=` | 当前项目任务编排 |
| `PUT` | `/api/workflow-definitions/{id}` | 保存当前项目任务编排新版本 |

企业内部适配完成后，验证重点是前端请求必须出现 `providerId`，并且切换迭代后 `projectId/spaceId/iterationId` 一起变化。`projectId` 应该形如 `enterprise-cloudereq:PROJ-1001`，`spaceId/iterationId` 应该是企业接口的稳定 id，而不是中文展示名。

## 扩展字段策略

公共表只保留通用字段。企业特有字段放进 `extraJson`：

```json
{
  "departmentId": "dept-101",
  "businessLine": "payments",
  "sourceUrl": "https://internal.example/work-items/FE-123",
  "securityLevel": "internal"
}
```

只有当字段需要被前端筛选/排序、工作流分支、权限判断或 join 查询时，才升级为正式列。

## 企业内部 AI 接入步骤

1. 实现 `EnterpriseCloudeReqProjectDataProvider`，注册为 Spring Bean。
2. 保证 `id()` 返回稳定唯一值，例如 `enterprise-cloudereq`。
3. 在 `snapshot()` 中调用内部项目、空间、迭代、工作项接口。
4. 映射 `ProjectContextDto` 和 `ProjectProviderWorkItemDto`，所有 external id 必须稳定。
5. 状态和优先级做全量映射，未知值使用安全 fallback，并写入 `extraJson.rawStatus/rawPriority`。
6. 启动 Bridge，打开运行设置，选择企业 provider。
7. 调 `POST /api/project-data-providers/sync`，确认 `project_data_sync_history.status=SUCCESS`。
8. 调 `GET /api/work-items?providerId=enterprise-cloudereq&projectId=enterprise-cloudereq:{externalProjectId}&spaceId={externalSpaceId}&iterationId={externalIterationId}` 验证数据隔离。
9. 切换迭代，确认 FE/US/TASK/WORK/BUG/VULN 统计和列表随迭代变化。
10. 打开任务编排页，确认 `/api/workflow-definitions?projectId=...` 按当前项目加载。

## 最小验收

- 切换 provider 后，项目管理页的项目、空间、迭代来自企业接口，不再出现本地 fixture。
- 切换迭代后，首页卡片和工作项列表只显示当前迭代数据。
- 同名项目/同名 Sprint 在不同 provider 下不会串数据。
- `project_data_sync_history` 能看到最近一次同步的 provider、状态、context 数和 work item 数。
- 任务编排保存的新版本带当前项目 `project_id`，启动该项目工作项时优先使用该项目编排。
