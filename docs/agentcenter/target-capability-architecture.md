# AgentCenter Target Capability Architecture

Status: planning draft

This document defines how AgentCenter capabilities should be rebuilt on top of
the OpenCode TypeScript/Web codebase. The old AgentCenter implementation is only
used as product requirement input; it is not the target architecture.

## North Star

AgentCenter should become an OpenCode-native product surface:

- OpenCode remains the runtime base: conversation, message parts, tool cards,
  file tree, file preview, diff view, prompt input, sessions, and provider/model
  execution.
- AgentCenter adds product boundaries: user, project, work item, workspace,
  workflow state, artifact registry, project skills, and project MCP.
- The browser opens a product workspace, not an arbitrary filesystem directory.
- Product state must survive refresh, session switching, and context
  compaction.
- The first visible experience should be reduced and stable: use OpenCode's
  native chat UI first, then add AgentCenter context panels only after the
  core boundaries are correct.

## What We Keep From OpenCode

These OpenCode capabilities should be inherited instead of rewritten:

| Capability | Decision | Reason |
| --- | --- | --- |
| Conversation timeline | Keep | Native OpenCode already has structured message parts, reasoning, tool, and text rendering. |
| Tool cards | Keep | Tool execution and message parts are already connected to session state. |
| File tree and preview | Keep | This is the stable base for code inspection; avoid rebuilding the previous fragile preview. |
| Diff and edits | Keep | OpenCode already understands file changes in the active directory. |
| Prompt input and attachments | Keep, extend with persistence | Native attachment handling exists; AgentCenter should make it refresh-safe and workspace-owned. |
| Session storage | Keep | OpenCode remains source of truth for messages and parts. |
| Skill tool mechanics | Keep, wrap with product registry | OpenCode already has skill discovery and a `skill` tool; AgentCenter should unify its sources. |
| MCP client/runtime | Keep, manage at project/workspace level | OpenCode has MCP primitives; AgentCenter should add desired-state lifecycle and ownership. |

## What We Hide Or Defer

The current AgentCenter branch should stay in a reduced mode while the product
boundary is being built.

| Area | Phase 1 decision |
| --- | --- |
| Session share/unshare | Hide |
| Session fork | Hide |
| Runtime MCP selector in chat | Hide until project-level MCP exists |
| Agent/model variant cycling shortcuts | Hide unless the product explicitly exposes them |
| Terminal panel | Hide for now |
| Raw directory open/copy actions | Hide |
| Server switcher/status popover | Hide for product mode |
| Custom AgentCenter chat timeline | Do not port |

The goal is not to remove upstream capability permanently. It is to prevent
half-owned controls from leaking into the first AgentCenter experience.

## Capability Map

| Previous pain point | Target design |
| --- | --- |
| Conversation UI hierarchy was confusing | Use OpenCode's native message rendering. Add only small AgentCenter context surfaces later. |
| File preview was unstable | Keep OpenCode file tree/preview/diff as the base. Only change entry and authorization boundaries. |
| Upload disappeared or broke after refresh | Register uploads as workspace artifacts, then pass artifact references into OpenCode prompt parts. |
| Skill behavior differed across features | One project/workspace skill registry feeds prompt context, skill tool, workflow tools, and UI. |
| MCP start/stop was unreliable | Add project/workspace MCP desired state, reconciler, health status, and idempotent lifecycle operations. |
| MCP was not project-scoped | Store MCP servers and bindings at project/workspace scope, with per-user secret references. |
| No strong user/workspace isolation | Browser uses workspace ids; server resolves ids to allowed directories after user authorization. |
| Workflow state was lost during compaction | Store workflow run state, events, artifacts, and checkpoints outside chat; inject a compact state capsule on resume. |
| Refresh relied on frontend memory | Reconstruct screens from OpenCode session state plus AgentCenter registries. |

## Ownership Boundaries

There should be one source of truth per entity.

| Entity | Owner | Notes |
| --- | --- | --- |
| OpenCode session/message/part | OpenCode | Do not duplicate chat history in AgentCenter tables. |
| Directory file tree/diff | OpenCode over resolved worktree | Worktree is resolved by AgentCenter before OpenCode sees it. |
| User/tenant/project/work item | AgentCenter | Product authorization and navigation boundary. |
| Workspace registry | AgentCenter | Maps product workspace id to a controlled worktree. |
| Skill catalog and bindings | AgentCenter | Produces OpenCode-compatible skill sources. |
| MCP registry and bindings | AgentCenter | Produces OpenCode-compatible MCP runtime config. |
| Upload/artifact registry | AgentCenter | Stores durable references for prompt inputs and generated outputs. |
| Workflow run/state/checkpoint | AgentCenter | Must not depend on chat summaries. |

## Target Domain Model

```text
Tenant
  -> User
      -> Project
          -> WorkItem
              -> Workspace
                  -> OpenCode Session
                  -> WorkflowRun
                  -> Artifact
          -> SkillCatalog
          -> McpServer
```

Minimum entities:

```ts
type AgentCenterUser = {
  id: string
  tenantId?: string
  displayName?: string
}

type AgentCenterProject = {
  id: string
  tenantId?: string
  ownerUserId: string
  name: string
  createdAt: number
  updatedAt: number
}

type AgentCenterWorkspace = {
  id: string
  tenantId?: string
  projectId: string
  userId: string
  workItemId?: string
  name: string
  worktree: string
  status: "creating" | "ready" | "error" | "archived"
  createdAt: number
  updatedAt: number
}

type AgentCenterSkillBinding = {
  id: string
  tenantId?: string
  projectId: string
  workspaceId?: string
  source: "project" | "workspace" | "url" | "builtin"
  uri: string
  enabled: boolean
  version?: string
}

type AgentCenterMcpBinding = {
  id: string
  tenantId?: string
  projectId: string
  workspaceId?: string
  serverId: string
  enabled: boolean
  desiredState: "running" | "stopped"
  lastHealth?: "unknown" | "healthy" | "unhealthy"
  updatedAt: number
}

type AgentCenterWorkflowRun = {
  id: string
  tenantId?: string
  projectId: string
  workspaceId: string
  workItemId?: string
  sessionId?: string
  status: "idle" | "running" | "waiting" | "blocked" | "failed" | "done"
  phase?: string
  state: Record<string, unknown>
  updatedAt: number
}

type AgentCenterArtifact = {
  id: string
  tenantId?: string
  projectId: string
  workspaceId: string
  workflowRunId?: string
  sessionId?: string
  kind: "upload" | "file" | "diff" | "report" | "preview" | "log" | "external"
  title: string
  uri: string
  metadata?: Record<string, unknown>
  createdAt: number
}
```

## User And Workspace Isolation

The key rule: the browser never chooses a raw directory.

Flow:

1. Browser calls AgentCenter APIs with an authenticated user context.
2. User selects a `workspaceId`.
3. Server verifies `{tenantId, userId, workspaceId}`.
4. Server resolves the controlled worktree path.
5. OpenCode receives the resolved directory/workspace context internally.
6. Session, event stream, artifact, MCP, and skill reads are filtered by the
   same user/workspace boundary.

Local development can use `AGENTCENTER_DEV_USER_ID` or
`x-agentcenter-user-id`. Production should replace this with authenticated
identity.

Path rules:

- `workspaceId` is opaque and must not contain path data.
- Worktrees are created under `AGENTCENTER_WORKSPACE_ROOT`.
- Every resolved path is canonicalized with `realpath`.
- Resolved worktree/artifact paths must stay under the user's workspace root.
- Symlink escape and `../` traversal must be rejected.
- Session creation from an AgentCenter workspace records the workspace binding.

See `docs/agentcenter/workspace-isolation-design.md` for the lower-level
directory design.

## Skill Design

OpenCode already has skill discovery and a `skill` tool. AgentCenter should not
invent separate skill behavior per feature.

Target:

- Project owns a skill catalog.
- Workspace may have a small overlay when a work item needs extra skills.
- All features read from the same effective skill set:
  - system prompt skill list
  - native `skill` tool
  - workflow tool planning
  - UI skill management
- Effective skill set is materialized at session start and workflow run start.
- Workflow runs store the skill binding/version snapshot they used.

Recommended source order:

```text
builtin OpenCode skills
  -> project `.opencode/skills`
  -> project registered skill URLs
  -> workspace overlay skills
```

Rules:

- Do not let each page or workflow load skills independently.
- Do not store product workflow state inside skill files.
- Skill catalog changes should be explicit and auditable.
- If a workflow run starts with skill version A, later version B should not
  silently change the meaning of that in-flight run.
- Keep OpenCode's skill compaction protection, but do not rely on chat history
  as the only record of important workflow decisions.

## MCP Design

MCP should become project/workspace infrastructure, not a chat toggle.

Target:

- Project defines MCP servers.
- Workspace binds a subset of project MCP servers.
- User-specific secrets are referenced, not copied into workspace files.
- Runtime has a reconciler that compares desired state with actual process
  state.
- Start/stop operations are idempotent and lock per `{workspaceId, serverId}`.
- Health status is stored and visible to the product UI.

State model:

```text
configured -> enabled -> desired running -> starting -> healthy
                                      -> unhealthy
                                      -> stopped
```

Lifecycle rules:

- "Enable MCP" changes desired state; it does not directly assume the process
  is healthy.
- Reconciler starts missing enabled servers and stops disabled ones.
- Health probes update status separately from config.
- Restart is `stop desired actual process if present` + `start` + `probe`.
- Startup failure records logs and leaves the binding in `unhealthy`, not in a
  misleading enabled/ready state.
- The chat UI receives only healthy effective tools unless a diagnostic view is
  opened.

This avoids the previous failure mode where a UI switch, a process, and a
runtime config could disagree.

## Upload And Artifact Design

OpenCode's prompt input and attachment processing should stay. AgentCenter adds
durability and ownership.

Target flow:

1. User adds a file/image in the prompt.
2. AgentCenter registers it as a workspace artifact with ownership metadata.
3. The prompt submits OpenCode-compatible parts that reference the artifact.
4. OpenCode session stores normal message parts.
5. On refresh, the prompt/session UI reloads attachments from artifact/session
   references instead of frontend memory.

Rules:

- Large binary payloads live under the workspace artifact root or object
  storage.
- Metadata lives in AgentCenter artifact registry.
- Prompt parts should carry stable artifact ids where possible.
- Artifact reads must check workspace authorization.
- Generated reports, diffs, logs, and previews use the same artifact registry.

This keeps the OpenCode conversation model intact while fixing refresh and
ownership issues.

## Workflow And Compaction Design

Workflow continuity must not depend on a long chat transcript.

Target:

- A workflow run has durable state outside OpenCode messages.
- AgentCenter tools write workflow events/checkpoints as they execute.
- OpenCode messages can reference workflow ids and artifact ids.
- On session start, resume, or compaction, AgentCenter provides a small
  "workflow state capsule" summarizing the current durable state.

Durable workflow state should include:

- current phase
- pending/complete steps
- blocking confirmations
- selected skills and MCP bindings
- important decisions
- linked artifacts
- last checkpoint
- next suggested action

AgentCenter tools:

```text
agentcenter_work_item_get
agentcenter_work_item_update
agentcenter_workflow_state_get
agentcenter_workflow_state_update
agentcenter_checkpoint_create
agentcenter_artifact_create
agentcenter_artifact_link
agentcenter_confirmation_request
agentcenter_confirmation_resolve
```

Compaction rule:

- OpenCode summary is useful context, not source of truth.
- AgentCenter workflow state is source of truth.
- The capsule is regenerated from durable state whenever needed.
- Critical workflow tool calls can be protected from pruning where OpenCode
  supports that, but correctness must still come from the durable store.

## API Surface

Initial product APIs should stay small and typed.

```text
GET  /agentcenter/me

GET  /agentcenter/project
POST /agentcenter/project

GET  /agentcenter/work-item
POST /agentcenter/work-item

GET  /agentcenter/workspace
POST /agentcenter/workspace
GET  /agentcenter/workspace/:workspaceId
POST /agentcenter/workspace/:workspaceId/open

GET  /agentcenter/skill-catalog
POST /agentcenter/skill-catalog/binding
PATCH /agentcenter/skill-catalog/binding/:bindingId

GET  /agentcenter/mcp
POST /agentcenter/mcp/server
POST /agentcenter/mcp/binding
PATCH /agentcenter/mcp/binding/:bindingId
POST /agentcenter/mcp/binding/:bindingId/restart

POST /agentcenter/upload
GET  /agentcenter/artifact/:artifactId

GET  /agentcenter/workflow-run/:runId
POST /agentcenter/workflow-run/:runId/event
POST /agentcenter/workflow-run/:runId/checkpoint
```

The Web app should call these APIs only for product metadata. Once a workspace
is opened, existing OpenCode SDK/session APIs continue to drive the chat and
file experience.

## Web Product Shape

Phase 1/2 Web should stay restrained:

```text
Home
  -> projects/workspaces/work items
  -> open workspace
      -> OpenCode native session UI
          -> optional AgentCenter context panel later
```

Do not rebuild:

- chat message renderer
- file tree
- file preview
- diff renderer
- tool card rendering

Add later only where product state is missing:

- workspace switcher
- current work item summary
- workflow run status
- artifact list
- MCP health status
- skill catalog status

## Implementation Plan

Before implementation, read `1026-feature-adaptation-plan.md`. It is the
compatibility matrix for deciding which old AgentCenter 1026 branch capabilities
are kept, translated, deferred, or dropped in this OpenCode-native branch.

### P0: Clean OpenCode Fork And Reduction

Status: started.

Done in the current branch:

- forked OpenCode as the implementation base
- pulled upstream TypeScript/Web source
- added AgentCenter feature gates
- hidden first-stage controls that do not match the product boundary yet
- kept OpenCode native chat/file/session UI

### P1: Workspace Boundary Foundation

Goal: make the product entry point workspace-based and user-isolated, while
preserving the 1026 scope shape of tenant, workspace, project, user, work item,
and run.

Build:

- AgentCenter user resolver for local dev and future auth integration
- workspace/project registry storage
- controlled workspace root resolver
- workspace list/create/open APIs
- Web home page that opens workspace ids instead of raw directories
- session creation/opening bound to workspace id

Acceptance:

- user A cannot list/open user B's workspace
- browser cannot pass arbitrary `/Users/...` paths as product entry
- refresh reconstructs the workspace/session page
- OpenCode file preview and chat still work inside the resolved worktree

### P2: Project Assets Foundation

Goal: make Skill, MCP, and upload consistent at project/workspace scope.

Build:

- project registry
- project/workspace skill binding registry
- effective skill set resolver
- project/workspace MCP registry and binding model
- MCP desired-state reconciler and health API
- workspace artifact registry
- upload API that returns durable artifact ids

Acceptance:

- the same effective skill set is used by prompt context, skill tool, workflow,
  and UI
- MCP restart is idempotent and health status is accurate
- uploaded files/images survive browser refresh
- artifact access is blocked across users/workspaces

### P3: Workflow Tool Layer

Goal: make AgentCenter workflow state durable and compaction-safe.

Build:

- run ledger inspired by the 1026 `runtime_operation` design
- workflow run registry
- workflow event/checkpoint store
- AgentCenter tools listed above
- workflow state capsule injection on session start/resume
- artifact links from workflow tools
- minimal workflow status surface in Web

Acceptance:

- long workflow can continue after OpenCode compaction
- refresh does not lose current phase, pending confirmations, or artifacts
- workflow run can be reconstructed from durable events/checkpoints
- OpenCode chat remains the user-facing conversation surface

### P4: Product Hardening

Goal: make it deployable for real multi-user use.

Build:

- production auth adapter
- tenant-aware authorization
- secret reference integration for MCP
- workspace archival/cleanup jobs
- audit logs
- quotas and limits
- operational health pages

Acceptance:

- tenant/user boundaries are tested
- secrets are never exposed to browser or stored in workspace files
- cleanup does not delete another user's data
- MCP failures degrade to clear unhealthy status, not broken chat state

## First Visible Slice

The fastest useful next slice is P1:

1. Add `GET /agentcenter/me`.
2. Add workspace registry and resolver.
3. Add `GET /agentcenter/workspace`.
4. Add `POST /agentcenter/workspace/:id/open`.
5. Replace the current home entry with workspace cards/list.
6. Keep the OpenCode session UI unchanged after opening.

This gives a visible product boundary quickly while avoiding the old mistake of
rewriting the chat experience before the runtime model is stable.

## Verification Plan

Each phase needs concrete checks.

P1:

- unit tests for path canonicalization and traversal rejection
- API tests for cross-user workspace access denial
- Web smoke test: list workspace, open session, refresh page
- file preview smoke test in the resolved worktree

P2:

- skill resolver tests for project plus workspace overlay
- MCP lifecycle tests for start, stop, restart, failed start, health probe
- upload refresh test
- artifact authorization tests

P3:

- workflow state update tests
- checkpoint restore tests
- compaction/resume test that proves state comes from durable workflow store
- artifact linking tests

Global:

- `bun typecheck`
- `bun run build`
- Playwright smoke against `http://127.0.0.1:5173`

## Rejected Approaches

| Approach | Why rejected |
| --- | --- |
| Port old AgentCenter Vue/Java Bridge shell | It recreates the unstable wrapper and duplicates OpenCode runtime concerns. |
| Let browser keep sending arbitrary directory paths | It cannot satisfy multi-user isolation. |
| Build a new chat UI first | It delays the core boundary work and repeats the previous conversation hierarchy problem. |
| Store workflow state only in messages | Compaction and summarization can remove or distort it. |
| Use per-feature skill loaders | Skill behavior becomes inconsistent across chat, workflow, and tools. |
| Treat MCP as a UI toggle only | Process state, health, secrets, and workspace scope need a server-owned lifecycle. |
