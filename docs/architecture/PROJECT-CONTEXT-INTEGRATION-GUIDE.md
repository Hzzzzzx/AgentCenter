# Project Context Integration Guide

> Status: frontend context management implemented; home overview stats are database-backed; global project-data provider switching, fixture sync, and normalized project context persistence implemented; enterprise provider pending
> Scope: project management page, title bar project context, enterprise data source sync, home overview aggregation

## Goal

Project Context is the workspace-level scope used by AgentCenter to decide which enterprise project, space, and iteration are active. The current frontend supports:

- Multiple project context configurations.
- A custom display project name.
- CloudeReq project, space, and iteration selection.
- One active configuration that drives the title bar.
- A `Sync Data` button that refreshes work items, confirmations, and the home overview from Bridge APIs.
- Home overview top metrics loaded from the database-backed `/api/work-items/overview` API.

The current implementation adds a Bridge-side project-data provider registry with fixture providers and persists provider snapshots into normalized project context tables. The next implementation step is to add an enterprise provider implementation while keeping the Web and Bridge API contracts stable.

## Current Frontend Shape

Files:

- `agentcenter-web/src/types/projectContext.ts`
- `agentcenter-web/src/views/ProjectContextSettings.vue`
- `agentcenter-web/src/components/shell/TitleBar.vue`
- `agentcenter-web/src/components/shell/AppShell.vue`
- `agentcenter-web/src/App.vue`

Core type:

```ts
export interface ProjectContextSelection {
  id: string
  project: string
  cloudeReqProject: string
  space: string
  iteration: string
}

export interface ProjectContextOptions {
  cloudeReqProjects: string[]
  spaces: string[]
  iterations: string[]
}
```

Current placeholder behavior:

- `App.vue` owns `projectContexts`, `activeProjectContextId`, and `projectContextOptions`.
- `ProjectContextSettings.vue` edits the active context and emits updates.
- `TitleBar.vue` displays `projectContext.project` and lets the user switch `projectContext.iteration`.
- `Sync Data` refreshes the Bridge-backed work item list, pending confirmations, and home overview stats.
- Context options still use frontend seed data until enterprise project-context APIs are implemented.

Home overview data source:

- `HomeOverview.vue` asks `useWorkItemStore().loadOverview()` on mount.
- `workItemApi.overview()` calls `GET /api/work-items/overview`.
- Bridge aggregates from persisted `work_item`, `workflow_instance`, `workflow_node_instance`, and `confirmation_request` data via `WorkItemService.getOverview()`.
- The frontend only falls back to local derivation from `/api/work-items` if the overview payload is unavailable.

## Target Backend APIs

Recommended Bridge endpoints:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/work-items/overview` | Return database-backed type metrics for the home top cards. Implemented as the current unscoped baseline. |
| `GET` | `/api/project-contexts` | List saved workspace project context configurations. |
| `POST` | `/api/project-contexts` | Create a project context configuration. |
| `PUT` | `/api/project-contexts/{id}` | Update a configuration. |
| `DELETE` | `/api/project-contexts/{id}` | Delete a configuration. Reject deleting the last active/only context unless a replacement is provided. |
| `POST` | `/api/project-contexts/{id}/activate` | Mark one configuration as active for the current user/workspace. |
| `GET` | `/api/project-context-options` | Return selectable CloudeReq projects, spaces, and iterations. |
| `POST` | `/api/project-context-options/sync` | Trigger enterprise data sync, persist refreshed mappings, and return refreshed options. |

Implemented provider registry endpoints:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/project-data-providers` | List registered project data providers and the current global provider. |
| `PUT` | `/api/project-data-providers/active` | Switch the global project data provider. |
| `GET` | `/api/project-data-providers/snapshot` | Return the active provider's projects, spaces, iterations, and fixture work item payload without syncing. |
| `POST` | `/api/project-data-providers/sync` | Sync the active provider payload into AgentCenter `work_item`, then return the snapshot. |

Recommended scoped additions after project context persistence lands:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/work-items?projectId=&spaceId=&iterationId=` | Return work items for the active context. |
| `GET` | `/api/work-items/overview?projectId=&spaceId=&iterationId=` | Return top card metrics for the active context. |

Suggested response shape:

```json
{
  "contexts": [
    {
      "id": "ctx-agentcenter",
      "project": "AgentCenter",
      "cloudeReqProject": "CloudeReq 研发项目",
      "space": "研发中台",
      "iteration": "Sprint 14",
      "active": true
    }
  ],
  "options": {
    "cloudeReqProjects": ["CloudeReq 研发项目"],
    "spaces": ["研发中台"],
    "iterations": ["Sprint 14"]
  }
}
```

`GET /api/work-items/overview` response shape:

```json
{
  "source": "DATABASE",
  "refreshedAt": "2026-05-09T15:30:00Z",
  "stats": [
    {
      "type": "FE",
      "total": 8,
      "runningCount": 1,
      "waitingCount": 7,
      "blockedCount": 0,
      "unstartedCount": 0,
      "completedCount": 0,
      "completedNodeCount": 12,
      "totalNodeCount": 24,
      "completionRate": 50,
      "nodeDistribution": [
        { "label": "方案设计 (HLD)", "count": 3, "priority": 1 }
      ]
    }
  ]
}
```

## Persistence Model

Implemented normalized project context tables:

| Table | Purpose | Key fields |
|-------|---------|------------|
| `project_context` | Provider-scoped project mapping. | `provider_id`, `external_project_id`, `project_name`, `external_cloude_req_project_id`, `cloude_req_project_name`, `active`, `extra_json` |
| `project_space` | Provider-scoped project space mapping. | `provider_id`, `project_context_id`, `external_space_id`, `space_name`, `extra_json` |
| `project_iteration` | Provider-scoped iteration mapping under a space. | `provider_id`, `project_context_id`, `project_space_id`, `external_iteration_id`, `iteration_name`, `status`, `start_at`, `end_at`, `extra_json` |
| `project_provider_setting` | Current global runtime selection. | `active_provider_id`, `active_project_context_id`, `active_project_space_id`, `active_project_iteration_id` |

`work_item` keeps its existing display-scope fields for API compatibility:

| Field | Current role |
|-------|--------------|
| `project_id` | Display project name used by current scoped queries. |
| `space_id` | Display space name used by current scoped queries. |
| `iteration_id` | Display iteration name used by current scoped queries. |

`work_item` also stores stable provider mapping fields:

| Field | Current role |
|-------|--------------|
| `provider_id` | Owning project data provider, for example `fixture-alpha` or `enterprise-cloudereq`. |
| `external_work_item_id` | Stable enterprise work item id. Use the external primary key, not the display code, when available. |
| `project_context_id` | Internal FK-like reference to `project_context.id`. |
| `project_space_id` | Internal FK-like reference to `project_space.id`. |
| `project_iteration_id` | Internal FK-like reference to `project_iteration.id`. |
| `extra_json` | Provider-specific metadata that should be preserved but is not queried frequently. |

Extension rule:

- Stable identifiers and high-frequency filters should become first-class columns.
- Display-only or provider-specific fields should go into `extra_json`.
- Complex repeated structures should become dedicated tables only after a concrete access pattern exists.

## Sync Rules

`POST /api/project-context-options/sync` should:

1. Fetch projects, spaces, and iterations from CloudeReq.
2. Store stable ids and display names.
3. Upsert work items into `work_item`, preserving local workflow/runtime state.
4. Preserve existing selected values when still valid.
5. Return warnings when a saved context references a removed external item.
6. Avoid silently changing the active context unless the active mapping is invalid.

Frontend should treat sync as an explicit user action:

- Disable the button while syncing.
- Show success/failure notification.
- Replace `projectContextOptions` with the response.
- Keep `projectContexts` unchanged unless backend returns normalized contexts.
- Refresh `/api/work-items`, `/api/confirmations?status=PENDING`, and `/api/work-items/overview` after sync completes.

Home top-card aggregation rules:

- `total` comes from persisted work items in the active scope.
- `completionRate` is `completedNodeCount / totalNodeCount`.
- `waitingCount` counts work items whose current stages have pending or in-conversation confirmations.
- `blockedCount` counts failed stages or blocked/failed workflow instances.
- `nodeDistribution` groups work items by current waiting/running/failed/next node, not by frontend mock labels.

Implemented provider sync rules:

1. Read the active provider snapshot through `ProjectDataProvider.snapshot()`.
2. Upsert project, space, and iteration rows from both `contexts` and `workItems`.
3. Upsert work items by `(provider_id, external_work_item_id)` when available, with a legacy fallback to `code` for existing local rows.
4. Preserve workflow/runtime state by updating only work item business fields and provider mapping fields.
5. Store active provider/project/space/iteration in `project_provider_setting`.
6. Keep current UI filtering compatible through `project_id`, `space_id`, and `iteration_id` display fields.

## Provider Registry

Bridge owns a stable provider contract:

```java
interface ProjectDataProvider {
  String id();
  String name();
  String description();
  ProjectDataSnapshotDto snapshot();
}
```

The registry discovers Spring beans implementing this interface and exposes them through runtime settings. The first implementation uses global process-level selection because user/workspace-level isolation is deferred.

Implemented fixture providers:

| Provider | Purpose |
|----------|---------|
| `fixture-alpha` | Local test source for AgentCenter / 平台接入 with Sprint 14 and Sprint 15 data. |
| `fixture-beta` | Local test source for 企业中台 / 安全治理 with Sprint 21, Sprint 22, and 长期治理 data. |

Enterprise rollout should add a new provider bean, for example `enterprise-cloudereq`, and switch `agentcenter.project-context.provider` or the runtime settings dropdown to that provider id. Web code and project-management UI should not change.

For implementation details, field mapping, and validation rules, see [ENTERPRISE-PROJECT-DATA-PROVIDER-GUIDE.md](./ENTERPRISE-PROJECT-DATA-PROVIDER-GUIDE.md).

Current global setting scope:

- One active project data provider for the running Bridge process.
- Provider switch updates project options and triggers sync through the active provider.
- User/workspace-specific provider selection is explicitly deferred.

## Frontend Wiring Plan

Replace placeholder state in `App.vue` with a store, for example `stores/projectContext.ts`:

- `contexts`
- `activeContextId`
- `options`
- `activeContext`
- `loadContexts()`
- `createContext()`
- `updateContext()`
- `deleteContext()`
- `activateContext()`
- `syncOptions()`

Then wire:

- `ProjectContextSettings.vue`
  - Use store actions instead of emitting all mutations upward.
  - Keep local optimistic editing only if backend latency becomes visible.
- `TitleBar.vue`
  - Keep receiving `projectContext` and `iterationOptions` as props.
  - On iteration change, call `updateContext` for the active context.
- Work item/workflow creation
  - Include active context ids in start/create requests once backend supports scoping.
- Home overview
  - Keep rendering `workItemStore.overview.stats` first.
  - Use local derivation only as an offline resilience fallback.
  - Pass active context query params once Bridge supports scoped work item APIs.

## Validation Checklist

- Only one active context per user/workspace.
- Project display name can be custom text.
- Enterprise options come from backend, not hard-coded frontend arrays.
- Deleting the active context selects another context or is blocked with a clear message.
- Title bar updates immediately after project name or iteration changes.
- Sync failures do not clear existing options.
- Home overview top cards update after `Sync Data`.
- Home overview numbers match the same database rows returned by `/api/work-items`.
- Tests cover create, edit, delete, activate, sync, and title-bar iteration update.

## Current Known Gaps

- No enterprise auth/permission check for CloudeReq sync yet.
- No stale mapping warning UI yet.
- No optimistic/error state in the current placeholder implementation.
- Project provider selection is global; user/workspace-level provider selection is deferred.
- Current scoped work item API still accepts display names. The normalized internal ids are persisted and ready for a later API tightening pass.
