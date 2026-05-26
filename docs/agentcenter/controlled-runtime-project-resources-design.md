# Controlled Runtime And Project Resources Design

Status: discussion draft
Last updated: 2026-05-25

This document records the current AgentCenter direction for controlled OpenCode
runtime directories and project-level Skill/MCP management. It is more specific
than the earlier workspace isolation notes: it defines what F7 Skill Management
and F8 MCP Management should mean in the OpenCode-native branch.

## Decision Summary

AgentCenter should not expose OpenCode's raw "open any local directory" model as
the main product entry.

The product model is:

```text
Tenant
  -> Project
      -> Project Skill Registry
      -> Project MCP Registry
      -> Work Item
          -> Work Item Runtime Workspace
              -> OpenCode Session
              -> Workflow Run
              -> Runtime Resource Snapshot
```

The important decision:

- F7 Skill Management manages project-level Skills.
- F8 MCP Management manages project-level MCP servers and bindings.
- Work item runtime workspaces consume the project-level effective resources.
- Work item directories may contain generated `.opencode` runtime config, but
  that generated content is a projection, not the source of truth.

## Current OpenCode Gap

OpenCode Web is directory-centric today:

- The home page can open arbitrary local directories.
- The route carries a base64-encoded directory, such as `/:dir/session/:id?`.
- `SDKProvider`, global sync, file tree, permissions, sessions, and terminal
  state are all keyed by `directory`.
- Skill discovery and MCP config are loaded from OpenCode config sources around
  the active directory.

That behavior is correct for upstream OpenCode, but not for AgentCenter as a
center service.

AgentCenter needs:

- server-owned runtime roots;
- tenant, project, user, and work item boundaries;
- project-level Skill and MCP registries;
- work-item runtime directories created from product state;
- resource snapshots so workflows continue after refresh, resume, and context
  compaction.

The existing repository-level `.opencode/` directory is upstream project config.
It is not the AgentCenter runtime root.

## Development Runtime Root

For local development, create a controlled empty root under this repository:

```text
/Users/hzz/workspace/AgentCenter-opencode-native/runtime-workspaces/
```

Suggested configuration:

```text
AGENTCENTER_RUNTIME_ROOT=./runtime-workspaces
AGENTCENTER_DEV_TENANT_ID=local-tenant
AGENTCENTER_DEV_PROJECT_ID=demo-project
AGENTCENTER_DEV_USER_ID=local-user
```

The first local directory layout should be:

```text
runtime-workspaces/
  tenants/
    local-tenant/
      projects/
        demo-project/
          project-resources/
            skills/
            mcp/
            snapshots/
          work-items/
            WI-001/
              workspace/
                .opencode/
              runs/
```

Rules:

- The runtime root is configurable and never hard-coded into business logic.
- All runtime paths are resolved from `{tenantId, projectId, workItemId}`.
- The browser must not send arbitrary absolute paths as the product entry.
- Every resolved path must be canonicalized and checked to stay under
  `AGENTCENTER_RUNTIME_ROOT`.
- The UI may show the runtime root in a developer/debug area, but product
  navigation should speak in tenant, project, and work item terms.

## Ownership Model

| Entity | Owner | Notes |
| --- | --- | --- |
| Tenant | AgentCenter auth or local dev resolver | Top-level isolation boundary. |
| Project | AgentCenter registry | Owns Skill and MCP registries. |
| Project Skill Registry | AgentCenter registry plus controlled filesystem storage | Source of truth for available project Skills. |
| Project MCP Registry | AgentCenter registry plus secret references | Source of truth for available project MCP servers. |
| Work Item | AgentCenter registry | Product task or workflow unit. |
| Work Item Runtime Workspace | AgentCenter resolver | Filesystem directory where OpenCode runs for a work item. |
| OpenCode Session | OpenCode native runtime | Conversation and message source of truth. |
| Workflow Run | AgentCenter registry/tool state | Durable product state, not stored only in chat. |
| Runtime Resource Snapshot | AgentCenter registry | Captures Skill/MCP versions used by a work item run. |

## F7 Skill Management

F7 should be renamed conceptually to project Skill management.

It manages:

- Skill package metadata;
- source type, such as builtin, uploaded, git, or URL;
- version or content hash;
- enabled/disabled state at project level;
- validation result;
- usage references from workflow templates or work item runs;
- audit information for upload, refresh, enable, disable, and update.

First visible UI should show:

- current tenant and project;
- project Skill metrics: total, enabled, invalid, referenced;
- search and status filter;
- Skill list;
- selected Skill details;
- upload and refresh entry points.

First visible UI should not yet perform destructive runtime behavior:

- no real delete;
- no unreviewed overwrite;
- no direct write into random active work item directories;
- no browser-only state pretending to be the registry.

Future Skill lifecycle:

```text
uploaded/imported
  -> validated
  -> enabled in project
  -> selected by workflow/template
  -> materialized into work item runtime workspace
  -> snapshotted for workflow run
```

When a work item starts, AgentCenter resolves the effective project Skills and
materializes them into the work item runtime workspace in an OpenCode-compatible
way, for example generated `.opencode/skills` content or generated config paths.

The project registry remains the source of truth.

## F8 MCP Management

F8 should be renamed conceptually to project MCP management.

It manages:

- MCP server definition;
- transport type, such as local, remote, HTTP, SSE, or stdio;
- project enabled state;
- desired state, such as running or stopped;
- health status;
- tool snapshot;
- secret references, not raw secrets;
- audit information for import, enable, disable, test, refresh tools, and
  restart.

First visible UI should show:

- current tenant and project;
- MCP metrics: servers, enabled, tools, unhealthy;
- search and health/status filter;
- MCP server list;
- selected MCP details;
- import, test connection, refresh tools, and refresh all entry points.

First visible UI should not yet manage real processes:

- no direct process kill/start from the browser;
- no raw secret editing;
- no assumption that "enabled" means "healthy";
- no per-session MCP toggle as the primary management model.

Future MCP lifecycle:

```text
defined
  -> enabled in project
  -> desired running/stopped
  -> reconciled by runtime service
  -> health checked
  -> tool snapshot recorded
  -> effective tools exposed to work item sessions
```

The UI changes desired project state. A reconciler later compares desired state
with actual runtime state and updates health.

## Effective Resource Projection

Work item sessions should use project-level resources through an explicit
projection step.

```text
Project Skill Registry
Project MCP Registry
        |
        v
Effective Project Resources
        |
        v
Work Item Runtime Workspace
        |
        v
Generated .opencode runtime config
        |
        v
OpenCode Session
```

Projection rules:

- A work item run records the exact Skill and MCP snapshot it used.
- A later project Skill update must not silently change an in-flight workflow.
- MCP tool snapshots should be recorded separately from server definitions.
- The generated `.opencode` directory under a work item workspace can be
  deleted and regenerated from the project registry plus the run snapshot.
- The OpenCode native Skill and MCP mechanics should still be used at runtime.

## Compaction And Workflow Continuity

Resource management must support context compaction.

Durable run state should include:

- tenant id;
- project id;
- work item id;
- OpenCode session id;
- workflow run id;
- selected Skill snapshot;
- selected MCP tool snapshot;
- current workflow phase;
- pending confirmations;
- linked artifacts;
- latest checkpoint or handoff summary.

On resume or after compaction:

1. AgentCenter reads the durable workflow run state.
2. AgentCenter reads the stored runtime resource snapshot.
3. AgentCenter regenerates a small workflow/resource capsule.
4. OpenCode receives the capsule as runtime context.
5. The product UI reconstructs status from AgentCenter state and OpenCode
   session state, not from frontend memory.

This keeps OpenCode's conversation summary useful without making it the only
source of product truth.

## Web Entry Design

The first accepted UI shape should be:

```text
Home
  -> Project / Work Item entry
  -> Resource Management
      -> Skills
      -> MCP Servers
  -> Open Work Item Session
      -> OpenCode native session UI
```

For the immediate F7/F8 work:

- keep a resource management route;
- make the page speak in project-level terms;
- show tenant/project/runtime root context at the top;
- do not claim the resources belong to the currently open raw directory;
- do not modify `session.tsx`, `MessageTimeline`, or `SessionSidePanel`.

OpenCode's raw local directory picker should move behind a development or
feature-gated path once the AgentCenter workspace entry exists.

## API Direction

The API should make project resource ownership explicit.

```text
GET  /agentcenter/me

GET  /agentcenter/projects
GET  /agentcenter/projects/:projectId/runtime-context

GET  /agentcenter/projects/:projectId/skills
POST /agentcenter/projects/:projectId/skills/upload
POST /agentcenter/projects/:projectId/skills/refresh
PATCH /agentcenter/projects/:projectId/skills/:skillId

GET  /agentcenter/projects/:projectId/mcp
POST /agentcenter/projects/:projectId/mcp/import
POST /agentcenter/projects/:projectId/mcp/refresh
POST /agentcenter/projects/:projectId/mcp/:mcpId/test
POST /agentcenter/projects/:projectId/mcp/:mcpId/refresh-tools
PATCH /agentcenter/projects/:projectId/mcp/:mcpId

POST /agentcenter/projects/:projectId/work-items/:workItemId/open
GET  /agentcenter/projects/:projectId/work-items/:workItemId/runtime-snapshot
```

The browser chooses product ids. The server resolves runtime directories.

## Data Shape

Early TypeScript shape:

```ts
type AgentCenterRuntimeContext = {
  tenantId: string
  projectId: string
  runtimeRoot: string
}

type ProjectSkill = {
  id: string
  tenantId: string
  projectId: string
  name: string
  version?: string
  source: "builtin" | "upload" | "git" | "url"
  enabled: boolean
  validation: "unknown" | "valid" | "invalid"
  contentHash?: string
  storageUri?: string
  updatedAt: number
}

type ProjectMcpServer = {
  id: string
  tenantId: string
  projectId: string
  name: string
  transport: "stdio" | "http" | "sse"
  enabled: boolean
  desiredState: "running" | "stopped"
  health: "unknown" | "healthy" | "unhealthy" | "disabled"
  toolCount: number
  secretRefs?: string[]
  updatedAt: number
}

type WorkItemRuntimeWorkspace = {
  id: string
  tenantId: string
  projectId: string
  workItemId: string
  directory: string
  status: "creating" | "ready" | "error" | "archived"
  createdAt: number
  updatedAt: number
}

type RuntimeResourceSnapshot = {
  id: string
  tenantId: string
  projectId: string
  workItemId: string
  sessionId?: string
  workflowRunId?: string
  skills: Array<{ skillId: string; version?: string; contentHash?: string }>
  mcps: Array<{ mcpId: string; toolSnapshotHash?: string }>
  createdAt: number
}
```

## Implementation Phases

### Phase A: Documented UI Model

Goal: make F7/F8 display the correct product model.

- Update resource page copy and structure to say project-level resources.
- Show local dev tenant/project/runtime root.
- Split Skill and MCP into clear tabs.
- Keep upload/import/test/refresh as visible but non-destructive entries.
- Keep the data mocked or read-only until the registry exists.

### Phase B: Runtime Root And Registry Foundation

Goal: introduce controlled runtime roots without changing OpenCode session UI.

- Add `runtime-workspaces/` as local development root.
- Add runtime root resolver.
- Add local tenant/project/user defaults.
- Add project resource registry storage.
- Add work item runtime workspace creation.
- Hide raw directory picking from normal AgentCenter entry.

### Phase C: Real Project Skill/MCP APIs

Goal: make F7/F8 real.

- Implement project Skill list, upload, validate, refresh, enable, disable.
- Implement project MCP list, import, desired state, test, health, refresh tools.
- Store secret references instead of raw secrets.
- Keep all operations scoped by tenant and project.

### Phase D: Work Item Runtime Projection

Goal: make OpenCode sessions consume project resources correctly.

- Generate work item runtime workspace content.
- Materialize effective `.opencode` resources.
- Create runtime resource snapshots.
- Start or resume OpenCode sessions inside the resolved work item workspace.
- Provide workflow/resource capsule on resume and compaction.

## Not In Current Scope

- F6 Project Context Settings.
- F10 Runtime Settings.
- Replacing OpenCode's conversation timeline.
- Replacing OpenCode file tree or file preview.
- Full production tenant auth and RBAC.
- Real MCP process orchestration in the first display-only UI pass.

## Open Questions

- Should project-level Skills be physically copied into each work item
  workspace, symlinked, or referenced through generated config paths?
- Should MCP tool snapshots be stored as JSON files under
  `project-resources/snapshots/` or only in the registry database?
- What is the first real work item id source: manual local mock, imported issue,
  or AgentCenter-created work item?
- When a project Skill changes, should new work item runs automatically pick the
  latest valid version, or require an explicit project resource publish step?
