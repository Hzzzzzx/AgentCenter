# Project Context Integration Guide

> Status: frontend context management implemented; home overview stats are database-backed; enterprise storage/sync adapters pending
> Scope: project management page, title bar project context, enterprise data source sync, home overview aggregation

## Goal

Project Context is the workspace-level scope used by AgentCenter to decide which enterprise project, space, and iteration are active. The current frontend supports:

- Multiple project context configurations.
- A custom display project name.
- CloudeReq project, space, and iteration selection.
- One active configuration that drives the title bar.
- A `Sync Data` button that refreshes work items, confirmations, and the home overview from Bridge APIs.
- Home overview top metrics loaded from the database-backed `/api/work-items/overview` API.

The next implementation step is to replace the frontend placeholder data with Bridge APIs backed by persistent storage and enterprise system sync.

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

Minimum table/entity fields:

| Field | Notes |
|-------|-------|
| `id` | Stable context id. |
| `user_id` or `workspace_id` | Decide whether context is user-scoped, workspace-scoped, or both. |
| `project` | Custom display name shown in the title bar. |
| `external_system` | Example: `CLOUDEREQ`. |
| `external_project_id` | Stable CloudeReq project id. Do not persist display name only. |
| `external_project_name` | Cached display name. |
| `space_id` / `space_name` | Enterprise space mapping. |
| `iteration_id` / `iteration_name` | Enterprise iteration mapping. |
| `active` | One active context per scope. Enforce uniqueness. |
| `created_at` / `updated_at` | Audit fields. |

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

- No Bridge API or database persistence yet for project context configurations.
- No enterprise auth/permission check for CloudeReq sync yet.
- No stale mapping warning UI yet.
- No optimistic/error state in the current placeholder implementation.
- Home overview aggregation is currently unscoped; add project/space/iteration filters with the project context API rollout.
