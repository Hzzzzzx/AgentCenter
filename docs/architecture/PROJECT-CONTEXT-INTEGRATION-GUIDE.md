# Project Context Integration Guide

> Status: frontend placeholder implemented, backend storage and sync pending
> Scope: project management page, title bar project context, enterprise data source sync

## Goal

Project Context is the workspace-level scope used by AgentCenter to decide which enterprise project, space, and iteration are active. The current frontend supports:

- Multiple project context configurations.
- A custom display project name.
- CloudeReq project, space, and iteration selection.
- One active configuration that drives the title bar.
- A `Sync Data` button reserved for enterprise data refresh.

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
- `Sync Data` currently appends a mock CloudeReq option.

## Target Backend APIs

Recommended Bridge endpoints:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/project-contexts` | List saved workspace project context configurations. |
| `POST` | `/api/project-contexts` | Create a project context configuration. |
| `PUT` | `/api/project-contexts/{id}` | Update a configuration. |
| `DELETE` | `/api/project-contexts/{id}` | Delete a configuration. Reject deleting the last active/only context unless a replacement is provided. |
| `POST` | `/api/project-contexts/{id}/activate` | Mark one configuration as active for the current user/workspace. |
| `GET` | `/api/project-context-options` | Return selectable CloudeReq projects, spaces, and iterations. |
| `POST` | `/api/project-context-options/sync` | Trigger enterprise data sync and return refreshed options. |

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
3. Preserve existing selected values when still valid.
4. Return warnings when a saved context references a removed external item.
5. Avoid silently changing the active context unless the active mapping is invalid.

Frontend should treat sync as an explicit user action:

- Disable the button while syncing.
- Show success/failure notification.
- Replace `projectContextOptions` with the response.
- Keep `projectContexts` unchanged unless backend returns normalized contexts.

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

## Validation Checklist

- Only one active context per user/workspace.
- Project display name can be custom text.
- Enterprise options come from backend, not hard-coded frontend arrays.
- Deleting the active context selects another context or is blocked with a clear message.
- Title bar updates immediately after project name or iteration changes.
- Sync failures do not clear existing options.
- Tests cover create, edit, delete, activate, sync, and title-bar iteration update.

## Current Known Gaps

- No Bridge API or database persistence yet.
- No enterprise auth/permission check for CloudeReq sync yet.
- No stale mapping warning UI yet.
- No optimistic/error state in the current placeholder implementation.
