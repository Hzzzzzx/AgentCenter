# Workspace Control Design

Status: implementation draft
Last updated: 2026-05-27

This document defines `workspace-control`, the control plane that turns
OpenCode from a raw directory-oriented runtime into a controlled
tenant/project/work-item/user runtime for AgentCenter.

## Decision Summary

Use `workspace-control` as the module name and design boundary.

Chinese name: 工作空间控制面.

`workspace-control` owns product-level runtime boundaries:

- identity resolution;
- project registry;
- work item registry;
- user workspace allocation;
- runtime directory resolution;
- runtime scope guard;
- execution lease;
- project resource projection;
- OpenCode session binding.
- opaque workspace route id generation.

It does not own OpenCode's message, tool rendering, file preview, diff, model,
or provider core. Those should remain native OpenCode behavior.

## Why This Exists

OpenCode currently starts from a directory. AgentCenter should start from
product concepts:

```text
tenantId + projectId + workItemId + userId
  -> controlled runtime directory
  -> OpenCode session
  -> native OpenCode tools, files, diff, and messages
```

The browser must not choose raw filesystem paths. It should only choose product
ids. `workspace-control` resolves those ids into an allowed runtime root after
identity and membership checks.

## Target Concepts

```text
Tenant
  -> Project
      -> Project Resources
          -> Skills
          -> MCP
          -> Resource Snapshots
      -> Work Item
          -> Shared State
          -> Shared Artifacts
          -> Execution Lease
          -> User Workspaces
              -> User Workspace
                  -> Runtime Scope
                  -> OpenCode Session
```

Development defaults:

```text
AGENTCENTER_RUNTIME_ROOT=./runtime-workspaces
AGENTCENTER_DEV_TENANT_ID=local-tenant
AGENTCENTER_DEV_PROJECT_ID=demo-project
AGENTCENTER_DEV_USER_ID=local-user
```

These defaults are only for local development. The model should still store and
pass tenant, project, user, and work item ids from the first implementation.

## Directory Layout

```text
runtime-workspaces/
  tenants/
    {tenantId}/
      projects/
        {projectId}/
          project-resources/
            skills/
            mcp/
            snapshots/

          work-items/
            {workItemId}/
              state/
              artifacts/
              canonical/

              users/
                {userId}/
                  workspace/
                    .opencode/
                  sessions/
                  runs/
```

Directory meanings:

| Directory | Owner | Visibility | Notes |
| --- | --- | --- | --- |
| `project-resources/skills` | workspace-control | project members | Project-level Skill source of truth or storage projection. |
| `project-resources/mcp` | workspace-control | project members | Project-level MCP definitions and bindings. |
| `project-resources/snapshots` | workspace-control | project members | Immutable effective resource snapshots used by runs. |
| `work-items/{workItemId}/state` | workspace-control | work item members | Durable work item state, lease, and metadata. |
| `work-items/{workItemId}/artifacts` | workspace-control | work item members | Shared artifacts promoted from user workspaces or runs. |
| `work-items/{workItemId}/canonical` | workspace-control | work item members | Optional final merged state for the work item. |
| `work-items/{workItemId}/users/{userId}/workspace` | user runtime | owning user and authorized runtime | OpenCode cwd for that user and work item. |
| `work-items/{workItemId}/users/{userId}/runs` | user runtime | owning user and authorized runtime | Per-run temporary output and evidence. |

## Collaboration Model

Do not make multiple users write into the same OpenCode cwd by default.

Shared:

- work item status;
- work item discussions and confirmations;
- artifacts;
- final diff or promoted output;
- project-level Skill/MCP resources.

Isolated:

- user workspace cwd;
- OpenCode session execution;
- tool calls;
- shell cwd;
- temporary run output.

The first collaboration mode should be:

```text
one work item
  -> shared state and artifacts
  -> one active executor lease
  -> multiple observers or reviewers
  -> per-user isolated workspaces
```

Later phases can add parallel exploration where multiple users submit patches
or artifacts from their isolated workspaces and promote them through review.

## Runtime Scope

Every OpenCode session opened through `workspace-control` must have a
`RuntimeScope`.

```ts
type RuntimeScope = {
  tenantId: string
  projectId: string
  workItemId: string
  userId: string
  sessionId: string
  workspaceId: string
  allowedRoot: string
  resourceSnapshotId?: string
}
```

`allowedRoot` is the canonical path to:

```text
runtime-workspaces/tenants/{tenantId}/projects/{projectId}/work-items/{workItemId}/users/{userId}/workspace
```

Rules:

- `allowedRoot` must be created by the server.
- `allowedRoot` must be canonicalized with `realpath`.
- `allowedRoot` must stay under `AGENTCENTER_RUNTIME_ROOT`.
- the browser must never send `allowedRoot` as a trusted value.
- session, file, tool, and shell operations must resolve from this scope.

## Agent Access Control

The agent must only read or write within the current `RuntimeScope.allowedRoot`,
unless a later reviewed promotion/read-only-sharing flow explicitly grants
access.

Guarded operations:

- file read;
- file write;
- edit;
- apply patch;
- grep;
- glob;
- artifact preview;
- upload extraction;
- diff;
- shell workdir;
- local MCP file references.

Path check:

```text
realpath(requestedPath).startsWith(realpath(allowedRoot))
```

The implementation must also reject symlink escapes. A path that starts inside
`allowedRoot` before resolution but points outside after resolution is not
allowed.

## Shell Boundary

Shell is the highest-risk surface. Application-level path checks are not enough
to guarantee process isolation once arbitrary commands are allowed.

Phase 1:

- shell cwd defaults to `allowedRoot`;
- custom shell workdir is denied unless it resolves under `allowedRoot`;
- OpenCode external directory permission defaults to deny;
- shell tool calls record tenant, project, work item, user, and session ids.

Production target:

- run shell/tool execution in an OS-level sandbox;
- use container, chroot, or per-run OS user isolation;
- mount only `allowedRoot` and reviewed resource projections;
- deny access to other users, work items, projects, and host paths by default.

## OpenCode Integration Boundary

Modify OpenCode only at the runtime entry and guard layer.

Allowed changes:

- add `packages/opencode/src/workspace-control/`;
- add resolver from product ids to runtime directories;
- add registry for project, work item, user workspace, and session binding;
- bind OpenCode session creation to a `RuntimeScope`;
- route AgentCenter Web through product ids instead of raw directories;
- add path/scope guard before file/tool/shell operations.

Avoid changes:

- OpenCode message schema;
- tool rendering schema;
- native file preview logic;
- native diff rendering logic;
- model/provider execution core;
- native conversation storage.

## First Implementation Slice

The first implementation should prove the controlled runtime path without
building the full product.

Implemented first-stage core:

- `packages/opencode/src/workspace-control/config.ts`
- `packages/opencode/src/workspace-control/identity.ts`
- `packages/opencode/src/workspace-control/open-work-item.ts`
- `packages/opencode/src/workspace-control/runtime-scope.ts`
- `packages/opencode/src/workspace-control/path-guard.ts`
- `packages/opencode/src/workspace-control/registry.ts`
- `packages/opencode/test/workspace-control/runtime-scope.test.ts`
- `packages/opencode/src/server/routes/instance/httpapi/groups/agentcenter.ts`
- `packages/opencode/src/server/routes/instance/httpapi/handlers/agentcenter.ts`
- `packages/agentcenter-web/src/main.tsx`

Scope:

- default tenant and user resolver;
- one seeded or local project;
- work item registry;
- user workspace creation;
- runtime directory resolver;
- OpenCode session binding;
- controlled session permission defaults that deny external directory access;
- first-stage AgentCenter HTTP API that opens sessions and proxies messages
  through session bindings instead of browser-provided raw directories;
- first-stage AgentCenter Web shell that selects project/work item and renders
  OpenCode `message + parts` payloads;
- original OpenCode Web `PromptInput` mounted in the AgentCenter shell through
  a narrow native context adapter. The adapter validates that native OpenCode
  requests stay on the bound runtime directory and forwards session list/get,
  message, diff, todo, children, config/provider/path/project reads,
  agent/command/LSP/MCP/VCS reads, question/permission reads, prompt, command,
  shell, abort, file, and search calls through `workspace-control`;
- native OpenCode todo rendering. The embedded composer calls the controlled
  `/agentcenter/session/:sessionId/todo` endpoint and renders OpenCode Web's
  original `SessionTodoDock`; `todo.updated` events flow through the same
  session-scoped event stream;
- native session metadata and message/part mutation compatibility. The browser
  adapter intercepts OpenCode routes for session update, share/unshare,
  single-message read, message delete, part delete, and part update, then
  forwards them to controlled AgentCenter endpoints bound to the current
  `RuntimeScope`;
- controlled session event stream at `/agentcenter/session/:sessionId/event`.
  Browser OpenCode contexts subscribe to this route instead of raw
  `/global/event`; the server filters events to the bound session/scope and
  rewrites event `directory` / `workspace` metadata to the opaque
  `workspaceId`;
- controlled prompt file references. When OpenCode's original `PromptInput`
  sends native `file://<workspaceId>/...` file parts, the server resolves them
  back to the bound `allowedRoot` through `workspace-control` and rejects
  sibling, absolute-path, or symlink escapes before OpenCode's Read flow runs.
  Line selections use the same native file part shape with `start` / `end`
  query parameters, preserving OpenCode's range-read behavior under the
  controlled path boundary;
- native OpenCode `SessionReview` embedded for real session diff payloads.
  AgentCenter does not reimplement diff review cards; it provides controlled
  `readFile` and prompt-context add/update/remove bridges so the native review
  line selection and line-comment flows still run inside the bound
  `RuntimeScope`;
- message file open bridge. File references rendered by OpenCode conversation
  turns can ask the controlled file panel to open a path, but the panel still
  reads content through the session binding and `allowedRoot` guard rather than
  trusting the browser as a filesystem authority;
- local Vite proxy guard that blocks raw browser directory routes such as
  `/config?directory=...` and raw `/global/event`. This is a development guard
  only; production isolation still belongs at the AgentCenter gateway/network
  boundary;
- controlled native session commands for abort, revert, unrevert, fork, and
  compact;
- client view that exposes product ids, a debug label, and a temporary
  opaque workspace directory token for the OpenCode Web context adapter.
  Browser routes use `w.<workspaceId>/session/:sessionId` instead of encoded
  host directories, and the browser does not receive `allowedRoot`;
- path guard primitive for later file/tool/shell integration.

Out of scope:

- full authentication;
- production authorization UI;
- real project Skill/MCP lifecycle;
- workflow engine;
- multi-user real-time editing;
- complex patch merge;
- artifact promotion UI.

## API Shape

Initial API can stay small:

```text
GET  /agentcenter/me
GET  /agentcenter/project
GET  /agentcenter/project/:projectId/work-item
POST /agentcenter/project/:projectId/work-item/:workItemId/open
GET  /agentcenter/session/:sessionId/scope
```

The implemented first slice exposes this through both a module API and a small
HTTP API.

Module API:

```ts
openWorkItemWorkspace({
  config,
  identity,
  registry,
  request: { projectId, workItemId, sessionId },
})
```

HTTP API:

```text
GET  /agentcenter/me
GET  /agentcenter/project
POST /agentcenter/project/:projectId/work-item/:workItemId/open
GET  /agentcenter/session/:sessionId/scope
GET  /agentcenter/session/:sessionId/event
GET  /agentcenter/session/:sessionId/message
GET  /agentcenter/session/:sessionId/message/:messageId
GET  /agentcenter/session/:sessionId/children
GET  /agentcenter/session/:sessionId/todo
PATCH /agentcenter/session/:sessionId
POST /agentcenter/session/:sessionId/message
DELETE /agentcenter/session/:sessionId/message/:messageId
DELETE /agentcenter/session/:sessionId/message/:messageId/part/:partId
PATCH /agentcenter/session/:sessionId/message/:messageId/part/:partId
POST /agentcenter/session/:sessionId/command
POST /agentcenter/session/:sessionId/shell
GET  /agentcenter/session/:sessionId/file
GET  /agentcenter/session/:sessionId/file/content
GET  /agentcenter/session/:sessionId/file/status
GET  /agentcenter/session/:sessionId/find
GET  /agentcenter/session/:sessionId/find/file
POST /agentcenter/session/:sessionId/abort
POST /agentcenter/session/:sessionId/revert
POST /agentcenter/session/:sessionId/unrevert
POST /agentcenter/session/:sessionId/fork
POST /agentcenter/session/:sessionId/compact
POST /agentcenter/session/:sessionId/share
DELETE /agentcenter/session/:sessionId/share
GET  /agentcenter/session/:sessionId/question
POST /agentcenter/session/:sessionId/question/:requestId/reply
POST /agentcenter/session/:sessionId/question/:requestId/reject
GET  /agentcenter/session/:sessionId/permission
POST /agentcenter/session/:sessionId/permission/:requestId/reply
GET  /agentcenter/session/:sessionId/native/agent
GET  /agentcenter/session/:sessionId/native/command
GET  /agentcenter/session/:sessionId/native/config
GET  /agentcenter/session/:sessionId/native/config/providers
GET  /agentcenter/session/:sessionId/native/formatter
GET  /agentcenter/session/:sessionId/native/lsp
GET  /agentcenter/session/:sessionId/native/mcp
GET  /agentcenter/session/:sessionId/native/path
GET  /agentcenter/session/:sessionId/native/permission
GET  /agentcenter/session/:sessionId/native/project
GET  /agentcenter/session/:sessionId/native/project/current
GET  /agentcenter/session/:sessionId/native/provider
GET  /agentcenter/session/:sessionId/native/question
GET  /agentcenter/session/:sessionId/native/vcs
```

The AgentCenter routes call the module API internally, create the native
OpenCode session inside the controlled `RuntimeScope.allowedRoot`, then serve
message reads, prompts, and file operations through the session binding. File
routes never accept raw OpenCode directory routing fields; they resolve the
server-side session binding, deny path escape through `workspace-control`, and
hide `.opencode` runtime state from user-facing file list/search results.

Prompt routes use the OpenCode native prompt contract. The controlled
`/message` endpoint accepts `PromptInput.parts` and forwards those parts into
`SessionPrompt.prompt` inside the bound `allowedRoot`. It still accepts the old
text-only shape as a temporary compatibility path for the first AgentCenter
shell. Native file parts are not trusted as browser filesystem paths: opaque
`file://<workspaceId>/relative/path` and `file:///<workspaceId>/relative/path`
forms are translated to the server-side `allowedRoot`, while `start` / `end`
query parameters are preserved for OpenCode's selected-line reads. Any URL or
source path outside the session scope is rejected. Controlled `/command` and
`/shell` endpoints forward OpenCode native command and shell payloads through
the same binding. Command file parts use the same opaque file URL rewrite and
path-escape rejection as prompt file parts.

Native read compatibility routes expose the OpenCode data shapes needed by the
embedded original PromptInput, but they resolve the directory from the
workspace-control session binding. The browser asks for
`/agentcenter/session/:sessionId/native/config` rather than
`/config?directory=/host/path`. Question and permission reads are filtered to
the bound session id; project reads expose the current bound project instead of
the host-level OpenCode project list.

Event compatibility follows the same rule. The AgentCenter shell and embedded
OpenCode contexts subscribe to `/agentcenter/session/:sessionId/event`; the
handler filters OpenCode global events to the bound session or allowed root,
then returns only opaque `workspaceId` metadata. Browsers must not subscribe to
raw `/global/event`, because the native stream carries host runtime directory
metadata.

Session command routes follow the same rule. `fork` calls OpenCode's native
session fork inside the bound allowed root, then binds the new session id back
to the same AgentCenter tenant/project/work-item/user scope. `compact` reuses
OpenCode's native compaction service and prompt loop; if the client does not
pass a model, the handler infers it from the latest user or assistant message
inside the controlled session.

Open response:

```ts
type OpenWorkItemResponse = {
  tenantId: string
  projectId: string
  workItemId: string
  userId: string
  sessionId: string
  workspaceId: string
  allowedRootLabel: string
}
```

`allowedRootLabel` is for debug display only. `workspaceId` is the browser route
token and the client-side OpenCode directory key, but it is not trusted for
filesystem access. The server resolves all trusted operations from the session
binding's `allowedRoot`; `allowedRoot` is never returned to the browser.

## Relationship To Existing Docs

- `workspace-isolation-design.md` introduced the need to hide raw directories
  behind product ids.
- `controlled-runtime-project-resources-design.md` defined project-level Skill
  and MCP registries.
- This document names the runtime boundary as `workspace-control` and adds the
  collaboration and agent access-control model.
