# 1026 Feature Adaptation Plan

Status: planning draft
Source branch: `origin/codex/from-2026-05-14-1026`
Target branch: `codex/opencode-native-base`

This document maps the useful AgentCenter 1026 branch capabilities into the
current OpenCode-native branch. The goal is to keep the product intent while
discarding the old Vue/Java Bridge shell as an implementation base.

## Position

The 1026 branch is a requirements and design source. It is not the runtime base.

Current target:

```text
OpenCode TypeScript/Web
  -> native conversation, tool parts, file tree, preview, diff, session runtime
  -> AgentCenter-owned user/project/workspace/run/workflow/artifact boundaries
```

1026 source:

```text
Vue Workbench + Java Bridge + OpenCode Adapter
  -> workflow state retention
  -> runtime operation ledger
  -> confirmation cards
  -> file-first artifacts
  -> project Skill/MCP resource management
  -> workspace/sandbox design
```

The migration rule is:

```text
keep product semantics
drop old shell mechanics
translate Bridge-owned state into OpenCode-native AgentCenter services/tools
```

## 1026 Capability Inventory

| 1026 capability | Keep? | OpenCode-native interpretation | Timing |
| --- | --- | --- | --- |
| Unified domain model: Tenant / Workspace / Project / User / Run / Artifact / Event | Yes | Use as AgentCenter product model above OpenCode sessions. | Foundation |
| Work item and workflow state | Yes | AgentCenter-owned durable state, referenced by OpenCode sessions/tools. | Foundation |
| Workflow state retention and context compression recovery | Yes | Replace Bridge DB + runtime_operation with AgentCenterRun / WorkflowRun + workflow state capsule. | P3 |
| Runtime operation ledger | Yes, rename | `runtime_operation` becomes `AgentCenterRun` or run ledger; it records dispatch, ack, tool events, outcome, and recovery pointers. | P3 |
| `AGENTCENTER_NODE_STATE` workflow protocol | Yes, later | Keep the protocol idea, but prefer AgentCenter tools and structured state where OpenCode supports it. | P3 |
| `CONTINUE_CURRENT` must re-enter workflow context | Yes | Any continue/resume inside a workflow must rebuild a state capsule before prompting OpenCode. | P3 |
| Workspace / user project workspace / run sandbox | Yes | Adopt as the target workspace model, adapted to OpenCode's single active directory model. | P1-P4 |
| File-first artifacts | Yes | Files in controlled workspaces are artifact sources; DB stores index/state, not primary content. | P2-P4 |
| Artifact promotion | Yes | Run output must be promoted to user/project space by confirmation or explicit action. | P4 |
| Confirmation cards | Yes | Keep as product action model; render through OpenCode-native UI surfaces later. | P4 |
| Notification event / delivery model | Partially | Keep event facts and read models; defer delivery channels and full notification center. | Later |
| Skill management | Yes | Convert to project/workspace skill catalog feeding OpenCode native Skill service/tool. | P2 |
| MCP management | Yes | Convert to project/workspace MCP desired-state registry and reconciler. | P2 |
| Runtime resource refresh | Yes, redesign | Avoid clearing active mappings blindly; model refresh as desired state + health + session/run compatibility. | P2 |
| Conversation event mapping | Partially | Do not port custom UI projection first; use OpenCode native conversation and preserve raw parts. | Now + later |
| Custom Vue workbench layout | No | Do not port. Start with OpenCode Web and add small AgentCenter surfaces only where needed. | Drop |
| Java Bridge HTTP/SSE adapter | No | Do not port. OpenCode is now the runtime/server base. | Drop |
| Old OpenCode Bridge process lifecycle | No | Replace with native OpenCode server lifecycle and AgentCenter registries. | Drop |
| External project providers / CloudReq integration | Defer | Useful later as project data connectors, not part of initial OpenCode-native workspace base. | Later |
| Long-term memory governance | Defer | Important but explicitly outside the current workspace adaptation pass. | Later |

## Immediate Documentation Adjustments

The current branch already has:

- `target-capability-architecture.md`
- `workspace-isolation-design.md`

This document adds the missing middle layer:

```text
1026 feature inventory
  -> OpenCode-native adaptation decision
  -> workspace implementation timing
```

It should be read before implementation planning so we do not accidentally:

- port the old Java/Vue implementation;
- rebuild the old conversation UI;
- ignore important 1026 product capabilities;
- mix memory design into the workspace foundation;
- treat OpenCode chat summaries as product state.

## What Should Be Folded Into The Current Branch

### 1. Product Scope Model

The current target docs already mention users and workspaces, but 1026 clarifies
that we need the full shape from day one:

```text
Tenant
  -> Workspace
    -> Project
      -> WorkItem
        -> Run
          -> RunSandbox

User
  -> UserProjectWorkspace
```

MVP values can be defaulted:

```text
tenantId = default
workspaceId = default or server-created
projectId = required once project registry exists
userId = local dev resolver first, auth later
runId = per workflow/tool execution
```

The important part is not implementing enterprise RBAC now. The important part
is avoiding a flat global workspace model that would need a migration later.

### 2. Workspace / Sandbox Physical Model

Replace the earlier user-only directory sketch with this target layout:

```text
$AGENTCENTER_WORKSPACE_ROOT/
  tenants/
    {tenantId}/
      workspaces/
        {workspaceId}/
          projects/
            {projectId}/
              shared/
              users/
                {userId}/
              runs/
                {runId}/
```

Meaning:

- `shared/`: project shared workspace, only confirmed artifacts and project
  material should land here.
- `users/{userId}/`: user project workspace, personal drafts and user-owned
  working material.
- `runs/{runId}/`: one execution sandbox, temporary outputs and tool evidence.

OpenCode adaptation:

- Phase 1 opens `users/{userId}/` or a registered project worktree as the active
  OpenCode directory so file tree/preview/diff remain native.
- Phase 2 creates `runs/{runId}/` for workflow/tool execution artifacts.
- Phase 3 introduces promotion from run sandbox to user or project space.
- Strict per-run code modification isolation can use git worktrees or patch
  promotion later.

### 3. Run Ledger

1026's `runtime_operation` is still the right idea, but not the right name or
implementation in this branch.

Target concept:

```text
AgentCenterRun
  id
  tenantId
  workspaceId
  projectId
  userId
  workItemId
  workflowRunId
  sessionId
  runSandboxId
  status
  dispatch
  ack
  lastEvent
  result
  createdAt
  updatedAt
```

This is the product run ledger. It answers:

- what was dispatched;
- which OpenCode session handled it;
- which workspace/sandbox was used;
- whether it was accepted/running/waiting/failed/done;
- what to resume after refresh, server restart, or compaction.

### 4. Workflow State Capsule

1026's context-compression documents are still highly relevant. The current
branch should keep this rule:

```text
OpenCode summary = conversation continuity
AgentCenter workflow state = product truth
```

OpenCode-native adaptation:

- durable workflow state lives in AgentCenter tables/registry;
- every workflow resume rebuilds a compact state capsule;
- compaction may preserve tool calls or summaries, but correctness comes from
  durable state;
- continue/resume inside a workflow must not bypass capsule injection.

### 5. File-first Artifacts

1026's file-first artifact design should be kept, but simplified for this
branch.

Target:

```text
file in controlled workspace
  -> artifact index
    -> preview/read through authorization
      -> optional promotion
```

OpenCode already has file tree, preview, and diff. We should not rebuild those.
AgentCenter should add:

- artifact metadata;
- workspace authorization;
- source run/session/work item links;
- promotion state;
- refresh-safe upload references.

### 6. Confirmation And Promotion

Confirmation is not just UI. It is the boundary that prevents temporary agent
output from becoming shared project truth.

Initial confirmation use cases:

- promote a run artifact into user project workspace;
- promote a user artifact into project shared workspace;
- approve workflow node advance;
- approve risky file/system/tool action;
- resolve blocked workflow state.

Later UI can render these as cards. The first implementation can be API/tool
driven.

### 7. Project Skill And MCP Resources

1026 had a full Java Bridge resource-management design. We keep the product
requirements and drop the implementation.

OpenCode-native adaptation:

```text
Project skill catalog
  -> effective skill set
    -> OpenCode Skill service + skill tool + workflow tools

Project/workspace MCP registry
  -> desired state
    -> reconciler
      -> healthy effective tools
```

Do not restore a chat-level MCP toggle as the main model. MCP should be project
or workspace infrastructure.

## What Should Not Be Folded In Now

| Area | Reason |
| --- | --- |
| Old Vue workbench shell | It duplicates OpenCode Web and brings back the previous conversation hierarchy problem. |
| Java Bridge runtime adapter | OpenCode TypeScript server is now the base. |
| Custom conversation projection | First phase should use OpenCode's native message/tool/file rendering. |
| Full notification delivery system | Confirmation/action truth is needed first; external delivery can wait. |
| Full workflow card UI | Durable workflow state should come before a custom UI layer. |
| Long-term memory governance | Valuable later, but it would blur the current workspace/sandbox scope. |
| Enterprise RBAC/ABAC | Keep fields and boundaries now; implement production authorization later. |
| External project providers | Defer until the local workspace and project registry are stable. |

## Workspace Timing

### Now: Documentation And Reduction

Purpose:

- keep the OpenCode-native base clean;
- document which 1026 capabilities survive;
- avoid bringing old shell behavior into the new branch.

Deliverables:

- this adaptation matrix;
- target capability docs linked from `docs/agentcenter/README.md`;
- no runtime code changes.

### P1: Workspace Entry Boundary

Goal:

- browser opens a product workspace, not a raw directory.

Build:

- local user resolver;
- AgentCenter project/workspace registry;
- controlled root resolver;
- workspace list/open API;
- home page entry that opens workspace ids;
- OpenCode session page remains native after entry.

Acceptance:

- different users cannot see each other's workspaces;
- browser cannot provide arbitrary absolute paths;
- refresh keeps the same workspace/session;
- OpenCode file tree/preview/diff still work.

### P2: Project/User Workspace Layout

Goal:

- introduce the physical model without forcing strict run sandbox execution
  immediately.

Build:

- create `shared/`, `users/{userId}/`, `runs/` folders;
- resolve a user project workspace for active OpenCode sessions;
- keep project shared files readable;
- keep user workspace writable;
- store workspace binding metadata.

Acceptance:

- same project + different users have different user workspaces;
- project shared space is not accidentally writable by the agent;
- path containment and symlink escape checks exist.

### P3: Run Ledger And Workflow Capsule

Goal:

- make workflow progress recoverable after refresh/compaction.

Build:

- `AgentCenterRun` / run ledger;
- workflow run state store;
- state capsule generation;
- continue/resume path that always rebuilds capsule when workflow context
  exists;
- minimal AgentCenter workflow tools.

Acceptance:

- a compacted OpenCode session can still recover current work item, workflow
  phase, pending confirmations, and artifacts from durable state;
- run status explains whether the agent is running, waiting, blocked, failed, or
  done.

### P4: Run Sandbox And Artifact Promotion

Goal:

- isolate execution outputs and make promotion explicit.

Build:

- create per-run `runs/{runId}` sandbox;
- capture files produced by a run as artifacts;
- promotion API/tool;
- confirmation for promotion into user/project space.

Acceptance:

- generated files first appear as run artifacts;
- confirmed files can move/copy into user or project space;
- rejected files do not pollute project shared space;
- artifacts remain previewable through OpenCode/file APIs or AgentCenter artifact
  APIs.

### P5: Skill, MCP, Upload Consistency

Goal:

- remove feature-specific resource behavior.

Build:

- project/workspace skill catalog;
- effective skill resolver;
- project/workspace MCP registry;
- MCP desired-state reconciler and health status;
- upload artifact registry.

Acceptance:

- chat, workflow, skill tool, and UI see the same effective skill set;
- MCP enable/restart is idempotent and health-based;
- uploaded files survive refresh and remain workspace-scoped.

### P6: Product Surfaces And Hardening

Goal:

- add richer AgentCenter surfaces after state boundaries are stable.

Build later:

- current work item panel;
- workflow status panel;
- confirmation/action cards;
- artifact list/promotion UI;
- notification read model;
- quotas, cleanup, audit;
- production auth and tenant-aware authorization.

## Current Branch Documentation Changes To Make

Recommended doc shape:

```text
docs/agentcenter/
  README.md
  target-capability-architecture.md
  workspace-isolation-design.md
  1026-feature-adaptation-plan.md
```

Future optional split:

```text
workspace-sandbox-model-design.md
artifact-promotion-design.md
workflow-state-retention-design.md
skill-mcp-resource-design.md
```

Do not create these split docs until implementation starts. Keeping one
adaptation matrix now is easier to review and reduces drift.

## Decision Record

| Decision | Result |
| --- | --- |
| Is 1026 the implementation base? | No. |
| Is 1026 a feature source? | Yes. |
| Should old Vue/Java shell be ported? | No. |
| Should OpenCode native chat/file/diff stay primary? | Yes. |
| Should workspace model include Tenant/Workspace/Project/User/Run fields now? | Yes, even if MVP defaults tenant/workspace. |
| Should memory be included in this pass? | No. |
| Should run sandbox be first visible feature? | No; workspace entry and user isolation come first. |
| Should artifact promotion be modeled now? | Yes, but implemented after workspace and run ledger. |
