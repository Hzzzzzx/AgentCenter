# AgentCenter Workspace Isolation Design

Status: draft for phase 2

## Current OpenCode Behavior

OpenCode Web is directory-centric today.

- The route carries a base64 encoded directory: `/:dir/session/:id?`.
- `packages/app/src/pages/directory-layout.tsx` decodes that route value and creates `SDKProvider`.
- `packages/app/src/context/global-sdk.tsx` creates directory-scoped clients through `createDirSyncContext(directory)`.
- `packages/sdk/js/src/v2/client.ts` sends the directory as `x-opencode-directory` and rewrites GET/HEAD requests to `?directory=...`.
- The server uses that directory to load an instance per request.

OpenCode also has an experimental workspace concept already:

- `packages/sdk/js/src/v2/client.ts` supports `experimental_workspaceID`, sent as `x-opencode-workspace`.
- `packages/opencode/src/control-plane/workspace.ts` and `workspace.sql.ts` define workspace records and sync/runtime behavior.
- `packages/opencode/src/session/session.ts` can associate sessions with `workspaceID`.

What it does not currently provide for our product goal:

- No AgentCenter user ownership model.
- No server-side allowlist that maps user-visible workspaces to permitted directories.
- No Web entry flow that prevents arbitrary browser-supplied directories.
- No work item binding above OpenCode sessions.

## Product Scenario

This fork is not a general local OpenCode desktop clone. It is a browser product where multiple users open AgentCenter and expect isolated coding workspaces.

The core difference from upstream OpenCode:

- upstream OpenCode: a person selects or opens local directories directly.
- AgentCenter OpenCode: a user opens product work items and workspaces; the server owns the actual working directories.

The design should optimize for these scenarios:

- multiple browser users use the same OpenCode deployment.
- each user only sees their own workspaces and work items.
- each workspace maps to a controlled server-side worktree.
- OpenCode's native conversation, file tree, preview, diff, and tool cards remain the primary experience.
- AgentCenter adds workflow and business state through stable tools and durable metadata, not by wrapping OpenCode with a separate old shell.
- workflow state survives summarization, compaction, browser refresh, and switching sessions.

## Target Model

AgentCenter should treat workspace as the product entry point, and directory as a server-owned implementation detail.

```text
Tenant
  -> User
      -> Work Item
          -> AgentCenter Workspace
              -> controlled worktree directory
              -> OpenCode project/session/file/diff runtime
              -> AgentCenter workflow state and artifacts
```

The browser should not be trusted to choose a raw filesystem directory. It may choose a `workspaceId`; the server resolves that id to a directory only after checking ownership.

Entity ownership:

| Entity | Source of truth | Notes |
|---|---|---|
| User / tenant | AgentCenter auth layer | Phase 2 local dev can use a fixed user resolver. |
| Work item | AgentCenter registry | Product-level task, requirement, ticket, or workflow entry. |
| Workspace | AgentCenter registry | Authorization boundary and server-owned directory mapping. |
| Directory / worktree | Filesystem under AgentCenter workspace root | Runtime detail; never chosen directly by browser. |
| OpenCode session/message/part | OpenCode native storage | Conversation source of truth remains OpenCode. |
| Workflow run/node/artifact | AgentCenter registry | Durable product state, referenced from OpenCode messages/tools. |

## Directory Layout

Use a single controlled workspace root, configurable by environment.

```text
$AGENTCENTER_WORKSPACE_ROOT/
  users/
    {userId}/
      workspaces/
        {workspaceId}/
          repo/       # OpenCode worktree, passed to existing directory-scoped runtime
          meta/       # AgentCenter metadata cache, not a product database
          artifacts/  # optional filesystem payloads for large generated files
```

Defaults for local development:

```text
AGENTCENTER_WORKSPACE_ROOT=~/.local/share/opencode-agentcenter/workspaces
AGENTCENTER_DEV_USER_ID=local
```

Rules:

- `workspaceId` is an opaque id, not a path.
- `worktree` must be canonicalized with `realpath`.
- resolved `worktree` must stay under `$AGENTCENTER_WORKSPACE_ROOT/users/{userId}/workspaces/{workspaceId}/repo`.
- workspace-owned artifact files must stay under `$AGENTCENTER_WORKSPACE_ROOT/users/{userId}/workspaces/{workspaceId}/artifacts`.
- browser routes and API payloads should not accept arbitrary absolute paths for session entry.
- OpenCode native file tree, file preview, diff, tool rendering, and session events continue to operate on the resolved `worktree`.
- deletion and archival operate on registry state first; physical cleanup can be asynchronous.

For local development only, a workspace may bind to an existing local repo through `source.type = "local-dev"`. That path must still be registered server-side and must not be accepted directly from the browser during session entry.

## Data Model

Phase 2 adds an AgentCenter workspace registry inside this OpenCode fork, separate from old AgentCenter Java/Vue code.

```ts
type AgentCenterWorkspace = {
  id: string
  tenantId?: string
  userId: string
  workItemId?: string
  name: string
  worktree: string
  source?: {
    type: "empty" | "git" | "local-dev"
    url?: string
    branch?: string
  }
  createdAt: number
  updatedAt: number
  archivedAt?: number
}

type AgentCenterWorkItem = {
  id: string
  tenantId?: string
  ownerUserId: string
  title: string
  status: "open" | "running" | "blocked" | "done" | "archived"
  primaryWorkspaceId?: string
  createdAt: number
  updatedAt: number
}

type AgentCenterWorkflowRun = {
  id: string
  tenantId?: string
  userId: string
  workItemId: string
  workspaceId: string
  sessionId?: string
  status: "idle" | "running" | "waiting" | "failed" | "done"
  state: Record<string, unknown>
  createdAt: number
  updatedAt: number
}

type AgentCenterArtifact = {
  id: string
  tenantId?: string
  userId: string
  workItemId?: string
  workspaceId: string
  workflowRunId?: string
  sessionId?: string
  kind: "file" | "diff" | "report" | "preview" | "log" | "external"
  title: string
  uri: string
  metadata?: Record<string, unknown>
  createdAt: number
}
```

Relationship to OpenCode tables:

- AgentCenter registry is the access boundary.
- OpenCode `workspaceID` can be used as the runtime workspace id when experimental workspace support is enabled.
- Session `workspaceID` should be set when creating sessions from an AgentCenter workspace.
- The existing `directory` path remains the compatibility layer for current OpenCode Web/session/file APIs.
- AgentCenter must not duplicate OpenCode message/part storage. It should reference OpenCode `sessionId` and durable artifact ids.
- AgentCenter workflow state must not be stored only in chat messages, because chat messages can be summarized or compacted.

## User Resolution

Phase 2 should use a simple pluggable resolver:

- local dev: `AGENTCENTER_DEV_USER_ID` or `x-agentcenter-user-id`.
- later product auth: authenticated session/JWT maps to `userId`.
- tenant-aware deployment: authenticated session/JWT maps to `{ tenantId, userId }`.

Every AgentCenter workspace API must resolve the user before reading or mutating workspace data.

Authorization rules:

- workspace lookup requires matching `tenantId` and `userId`, unless explicit sharing is added later.
- work item lookup requires matching owner/member scope.
- artifact lookup requires matching workspace/work item scope.
- event streams should only publish workspace/session events visible to the resolved user.

## API Shape

Add a small AgentCenter API group on the OpenCode server. Keep it separate from the OpenCode generated SDK until the shape stabilizes; then generate typed SDK methods.

```text
GET  /agentcenter/me
GET  /agentcenter/work-item
POST /agentcenter/work-item
GET  /agentcenter/workspace
POST /agentcenter/workspace
GET  /agentcenter/workspace/:workspaceId
POST /agentcenter/workspace/:workspaceId/open
GET  /agentcenter/workspace/:workspaceId/artifact
GET  /agentcenter/workflow-run/:runId
POST /agentcenter/workflow-run/:runId/event
```

Expected response shape:

```ts
type WorkspaceSummary = {
  id: string
  name: string
  status: "ready" | "creating" | "error" | "archived"
  updatedAt: number
}

type WorkspaceOpenResult = {
  workspaceId: string
  workItemId?: string
  directorySlug: string
  sessionHref: string
}
```

The `directorySlug` is generated server-side from the resolved worktree. The Web can still navigate into the existing `/:dir/session` route for phase 2, but it gets that slug from the server instead of constructing it from arbitrary user input.

The long-term route should hide raw directory slugs from product URLs:

```text
/agentcenter/workspace/:workspaceId/session/:sessionId?
```

That route can internally mount the existing OpenCode directory/session layout after resolving the workspace. Phase 2 can keep `/:dir/session` as an internal compatibility path to reduce risk.

## Web Entry Flow

Phase 2 Web changes should stay small:

1. Home page calls `GET /agentcenter/workspace`.
2. Home renders AgentCenter workspaces instead of generic OpenCode `Projects / Add project`.
3. Click workspace calls `POST /agentcenter/workspace/:id/open`.
4. Web navigates to the returned `sessionHref`.
5. Existing OpenCode session UI takes over.

This keeps the first visible product difference focused on workspace isolation while preserving OpenCode conversation, file preview, and diff behavior.

After phase 2:

- the home page should speak in work items/workspaces, not arbitrary projects.
- OpenCode's file tree remains visible after entering a workspace.
- model picker and prompt input can stay native.
- hidden first-phase controls remain behind `AgentCenterFeatures` until explicitly needed.

## Runtime Boundary

Runtime execution should still use OpenCode's native instance model.

```text
browser
  -> AgentCenter workspace API
      -> resolve authorized workspace
          -> OpenCode directory-scoped SDK/client
              -> OpenCode tools, file APIs, sessions, events
```

Important boundary rules:

- AgentCenter owns workspace authorization and product metadata.
- OpenCode owns coding runtime, tools, messages, file preview, diffs, and session events.
- AgentCenter tools can write product metadata, but should not bypass OpenCode's tool/event model for coding actions.
- OpenCode tools should execute inside the resolved workspace directory only.

## AgentCenter Tool Design

Workflow should be added as OpenCode tools/plugins rather than an external old Bridge wrapper.

Candidate tools:

```text
agentcenter_work_item_get
agentcenter_work_item_update
agentcenter_workflow_state_get
agentcenter_workflow_state_update
agentcenter_artifact_create
agentcenter_artifact_link
agentcenter_checkpoint_create
```

Tool rules:

- Tools receive `workspaceId`, `workItemId`, `workflowRunId`, and `sessionId` from runtime context, not from model-invented free text.
- Tools validate the resolved user/workspace before mutating metadata.
- Tools emit compact message parts for UI display, but durable state is stored in AgentCenter tables.
- Tools should be idempotent where possible; repeated calls with the same operation id should not duplicate artifacts or workflow events.
- Tool cards in the conversation should be reconstructed from tool calls plus durable metadata, not from ad hoc frontend parsing.

This is the stable replacement for the old Bridge-shell adaptation: OpenCode remains the agent runtime, AgentCenter semantics enter through first-class tools and server-owned metadata.

## Compaction And Workflow Continuity

OpenCode summarization/compaction should compress conversation text, not product state.

Durable state that must survive compaction:

- work item fields and status.
- workflow run status and node states.
- artifact ids, titles, and URIs.
- approval/confirmation decisions.
- key checkpoints and handoff summaries.

Runtime prompt strategy:

1. Keep the full durable workflow state in AgentCenter storage.
2. Generate a small "workflow state capsule" when a session starts or resumes.
3. Inject only the relevant capsule into OpenCode context.
4. When compaction happens, preserve the capsule source data outside the chat summary.
5. Let AgentCenter tools update durable state during the conversation.

This avoids losing workflow progress when OpenCode shortens chat history. The compressed OpenCode summary helps the model continue the conversation; AgentCenter durable state tells the product what has actually happened.

## Security And Isolation Layers

Minimum phase 2 layers:

1. Identity: resolve `{ tenantId?, userId }` for every AgentCenter API call.
2. Registry: allow only workspace ids owned by that identity.
3. Path: canonicalize and enforce workspace root containment.
4. Runtime: create OpenCode clients from resolved server-side directories, not browser paths.
5. Events: filter workspace/session events by visible workspaces.
6. Artifacts: store artifact metadata by workspace/work item and keep payloads under the workspace artifact root.

Later hardening:

- per-workspace process sandboxing.
- container or VM isolation for untrusted users.
- quotas for disk, process time, and concurrent sessions.
- background cleanup of archived workspaces.
- audit log for workspace open, tool calls, artifact writes, and approvals.

## Implementation Plan

Phase 2A: registry and resolver

- Add AgentCenter workspace/work item/workflow/artifact schemas.
- Add local user resolver with `AGENTCENTER_DEV_USER_ID`.
- Add workspace root resolver with path containment checks.

Phase 2B: API

- Add `/agentcenter/me`.
- Add workspace list/create/open.
- Return server-generated `sessionHref`.
- Reject cross-user and unknown workspace ids.

Phase 2C: Web entry

- Replace home project list with AgentCenter workspaces.
- Keep existing OpenCode session page after opening a workspace.
- Keep raw project picker behind `AgentCenterFeatures.rawWorkspaceActions`.

Phase 2D: runtime context

- Pass resolved workspace metadata into OpenCode client/session creation.
- Use OpenCode `workspaceID` where it is stable enough.
- Keep `directory` compatibility path until native workspace routes are complete.

Phase 2E: workflow foundation

- Add minimal AgentCenter tool stubs for workflow state read/update and artifact create.
- Store state durably outside messages.
- Render basic tool cards through OpenCode's native message/tool part system.

## What Not To Do In Phase 2

- Do not reintroduce the old AgentCenter Java Bridge.
- Do not rebuild OpenCode conversation cards.
- Do not replace OpenCode file preview/diff.
- Do not implement workflow cards yet.
- Do not implement full production auth/RBAC yet.
- Do not let browser-provided paths bypass workspace ownership checks.
- Do not make OpenCode session messages the only source of workflow truth.
- Do not expose `$AGENTCENTER_WORKSPACE_ROOT` paths in product UI unless explicitly in a developer/debug view.

## Rejected Alternatives

| Alternative | Rejection reason |
|---|---|
| Keep raw OpenCode directory picker as the main entry | Does not provide user isolation and makes browser path input part of the trust boundary. |
| Rebuild the old AgentCenter Bridge shell | Recreates the unstable adaptation layer and loses OpenCode Web's native conversation/file quality. |
| Store workflow progress only in chat summaries | Compaction can lose product state; workflow must be durable and queryable. |
| Create a separate repo clone per session by default | Too expensive and fragments history; use workspace as the durable unit, session as conversation unit. |
| Fully replace OpenCode workspace internals immediately | Higher merge risk with upstream; wrap with AgentCenter registry first, then integrate deeper once stable. |

## Phase 2 Acceptance

- A local user sees only their registered workspaces on the home page.
- Opening a workspace enters the normal OpenCode session UI.
- Raw directory picking is not the primary entry path.
- API rejects unknown or cross-user workspace ids.
- API never resolves a workspace to a path outside the controlled root.
- Existing OpenCode session/file/diff UI still works after entering a workspace.
- Workflow state can be updated through a durable AgentCenter API/tool path without relying on chat history.
- A compacted session can still reconstruct work item, workspace, artifact, and workflow status from durable state.
