# OpenCode Native Branch Tracker

Status: active tracking document
Branch: `codex/opencode-native-base`
Remote: `origin/codex/opencode-native-base`
Last updated: 2026-05-27

This document tracks what has actually changed after importing the OpenCode
TypeScript/Web codebase into the AgentCenter repository. It is intentionally
separate from architecture docs: this is the branch ledger, not the target
design.

## Tracking Rules

- Update this file for every AgentCenter-specific change on this branch.
- Separate committed implementation, planning-only docs, and disposable mocks.
- Record user feedback when a visible implementation is not the desired target.
- Do not treat old AgentCenter Vue/Java code as implementation source unless a
  future decision explicitly says so.
- Keep OpenCode upstream capability decisions traceable so future rebases or
  cleanups know what was intentionally changed.

## Baseline

| Commit | Type | What changed | Status |
| --- | --- | --- | --- |
| `9563448a` | baseline import | Imported the native OpenCode TypeScript/Web source as a clean orphan branch inside the AgentCenter repo. | Keep |

Baseline intent:

- OpenCode is the runtime and Web base.
- Old AgentCenter code is requirement reference only.
- No Java Bridge, Vue workbench, or old custom chat shell was ported.

## Changes After Baseline

| Commit | Type | Files | What changed | Status |
| --- | --- | --- | --- | --- |
| `493a3d8d` | docs | `docs/agentcenter/*` | Added AgentCenter-native planning docs for 1026 feature adaptation, target capability architecture, and workspace isolation. | Keep as planning |
| `b163c349` | app mock | `packages/app/src/app.tsx`, `packages/app/src/pages/home.tsx`, `packages/app/src/pages/agentcenter-resource-center.tsx`, `docs/agentcenter/1026-feature-adaptation-plan.md` | Added a visible `能力中心` entry and mock Skill/MCP management page. | Superseded by project-level resource management redesign in the current working tree |

## Current Implemented Delta

The previous AgentCenter pages under OpenCode Web are being removed from the
current working tree. This is an intentional correction after user feedback:
AgentCenter display work should not be implemented inside OpenCode's own Web
shell.

Current boundary:

- `packages/app` remains the upstream-shaped OpenCode Web application.
- AgentCenter display work belongs in `packages/agentcenter-web/`.
- The first UI target is parity with the old 1026 `agentcenter-web` frontend
  display shell.
- OpenCode conversation rendering is integrated through the separate
  AgentCenter shell and `workspace-control` routes. The shell now reuses
  OpenCode UI `SessionTurn` / `message-part` and has copied/adapted the native
  question and permission composer docks. A first controlled file side panel is
  now backed by `workspace-control` file list/search/content routes and OpenCode
  UI file rendering primitives, and it can add files or selected text ranges
  into OpenCode's original `PromptInput` context list through the native
  `PromptProvider`. The first native session commands are now
  exposed through controlled AgentCenter routes for abort, revert, unrevert,
  fork, compact, command, and shell. Prompt submission now accepts OpenCode
  native `PromptInput.parts` payloads through the controlled AgentCenter route,
  while keeping the old text-only payload as compatibility. The visible composer
  now mounts OpenCode Web's original `PromptInput` component inside a narrow
  native-context adapter, and routes prompt/command/shell/abort/file-search
  traffic back through `workspace-control`. Changed-file review now embeds
  OpenCode UI's native `SessionReview` component for real `session.diff`
  payloads, including native diff style switching, accordion behavior, file
  open action, line selection, and review line-comment submission into the
  original PromptInput context list. Message-rendered file references, user
  file attachments, tool file headers, and turn diff file names can also route
  to the controlled right-side file panel without exposing browser-selected raw
  directories. The browser route now uses an
  opaque `w.<workspaceId>/session/:id` token rather than a base64 encoded host
  directory, and the adapter also handles native session list/get/message/diff
  reads so the dev proxy does not need raw `/session` exposure. Native
  config/provider/path/project plus agent/command/LSP/MCP/VCS/question/permission
  reads now use `/agentcenter/session/:id/native/*` routes, so the browser no
  longer sends raw OpenCode `?directory=/host/path` reads for the embedded
  PromptInput. Session events now use `/agentcenter/session/:id/event`, which
  filters to the bound session/scope and rewrites event directory metadata to
  the opaque `workspaceId`; the browser no longer subscribes to raw
  `/global/event`. The browser also no longer receives the server-side
  `allowedRoot`; embedded OpenCode contexts use the opaque `workspaceId` as
  their client directory key. The local Vite proxy rejects raw directory-routed
  native reads and raw global events, while production still needs
  gateway/network isolation from the raw OpenCode port. Native prompt file
  parts using `file://<workspaceId>/...` are translated server-side to the
  bound `allowedRoot` and rejected if they escape the runtime scope. Native
  command file parts now use the same rewrite before entering OpenCode's
  command handler. The AgentCenter shell treats OpenCode `message.updated` and
  `message.part.updated` as create-or-update events, and buffers early
  `message.part.updated` / `message.part.delta` payloads until their parent
  message arrives, so native `SessionTurn` rendering is not dependent on a
  later bootstrap refresh. Controlled bootstrap and message snapshot responses
  now also scrub server-side `allowedRoot` strings back to the opaque
  `workspaceId`, matching the event stream behavior for native file context
  parts. The original OpenCode `PromptInput` slash popover now receives a
  narrow AgentCenter command registration for `/undo`, `/redo`, `/compact`, and
  `/fork`, with actions routed through workspace-control callbacks; native
  `session.summarize` traffic is translated to the controlled compact route.
  Native session todo and child-session reads are no longer stubbed in the
  embedded PromptInput adapter: `/session/:id/todo` and
  `/session/:id/children` now route through controlled AgentCenter endpoints,
  and the AgentCenter composer mounts OpenCode Web's original
  `SessionTodoDock` for real `todo.updated` state. Native session metadata and
  message/part mutation routes are also intercepted: session update,
  share/unshare, single-message read, message delete, part delete, and part
  update now go through workspace-control instead of raw OpenCode session
  routes.
  Full
  OpenCode side panel tabs/context tools, full command palette outside the composer, Skill/MCP
  management, and workflow remain later explicit integrations.
- Workflow implementation remains deferred.

Current implementation files:

| File | Purpose |
| --- | --- |
| `packages/agentcenter-web/` | Separate AgentCenter frontend package. It owns the AgentCenter shell and now renders OpenCode conversations through OpenCode UI `SessionTurn` / `message-part` components. |
| `packages/agentcenter-web/src/main.tsx` | Opens controlled project/work-item sessions, routes as `w.<workspaceId>/session/:id`, restores routed sessions from `/agentcenter/session/:id/scope`, bootstraps through `/agentcenter/session/:id/bootstrap`, subscribes to `/agentcenter/session/:id/event`, loads native provider/agent render context through controlled routes, applies OpenCode `session/message/part/status/diff/question/permission/todo` SSE events incrementally without `/message` polling fallback, sends prompts as native `PromptInput.parts`, exposes stop/revert/unrevert/fork/compact actions, mounts the controlled file panel, and routes message file clicks to that panel. |
| `packages/agentcenter-web/src/opencode-session/native-prompt-composer.tsx` | Mounts the original OpenCode Web `PromptInput` component with the required native providers and a workspace-control fetch adapter. The adapter blocks directory drift and routes native session list/get/update/message/diff/todo/children/share/unshare/message delete/part update/part delete, controlled session events, config/provider/path/project, agent/command/LSP/MCP/VCS/question/permission, prompt, command, shell, abort, file list/content/status, and find calls through the bound AgentCenter session. It also mounts OpenCode Web's original `SessionTodoDock` for controlled todo state and exposes the minimal bridge used by the controlled file panel and native review component to add, update, or remove file/comment context in OpenCode's native prompt context. |
| `packages/agentcenter-web/src/opencode-session/controlled-file-panel.tsx` | Adds the AgentCenter-controlled file side panel: native OpenCode `SessionReview` for real session diffs, workspace tree, file search, text preview, OpenCode UI line selection for text files, message-click file opening, and add-to-prompt-context action through OpenCode UI `File` / `FileIcon`, without browser-selected directories. |
| `packages/agentcenter-web/src/opencode-session/session-question-dock.tsx` | Copied/adapted from OpenCode Web's native question dock for quick-pick questions, multi-select, custom answer, keyboard navigation, reply, and reject. |
| `packages/agentcenter-web/src/opencode-session/session-permission-dock.tsx` | Copied/adapted from OpenCode Web's native permission dock for allow once, allow always, and deny decisions. |
| `packages/agentcenter-web/README.md` | Defines the separate frontend boundary, runtime proxy, controlled directory rule, and event-driven conversation rendering contract. |
| `packages/opencode/src/workspace-control/` | Adds the first `workspace-control` module for dev identity, runtime root resolution, opaque client path resolution, path guarding, session binding, and controlled session permissions. |
| `packages/opencode/src/server/routes/instance/httpapi/groups/agentcenter.ts` | Adds typed AgentCenter control-plane HTTP routes for identity, project/work item listing, session open, scope, event stream, bootstrap, session messages/todos/children, session update/share/unshare, message/part mutation, native-parts prompt, controlled command/shell, controlled file list/search/content/status, abort, revert/unrevert, fork, compact, question, permission, and native read compatibility. |
| `packages/opencode/src/server/routes/instance/httpapi/handlers/agentcenter.ts` | Implements the AgentCenter routes by resolving product ids through `workspace-control` and running OpenCode session, event filtering/sanitization, prompt, command, shell, file, todo, children, session update/share/unshare, message/part mutation, question, permission, fork, and compact operations inside the bound allowed root. |

## User Feedback On Current Mock

On 2026-05-25, after seeing the first page, the user said this was not the
intended result. The page has since been redirected toward the confirmed
project-level resource model: F7/F8 manage project-level Skill/MCP registries,
while work item runtime workspaces consume generated effective resources.

Implication:

- Do not continue the custom mock shell under `packages/app`.
- Rebuild the visible AgentCenter frontend from the 1026 display shell as a
  separate frontend.
- Keep backend/runtime API planning aligned with the project-level model, but do
  not let that planning invent a new visible product shell.

## Planning-Only Delta

The following docs were added or updated, but they do not change runtime
behavior:

| Document | Purpose |
| --- | --- |
| `docs/agentcenter/1026-frontend-feature-cards.md` | Breaks the 1026 Vue frontend into page-level feature cards for UI/function selection. |
| `docs/agentcenter/1026-feature-adaptation-plan.md` | Maps old 1026 AgentCenter capabilities into OpenCode-native decisions. |
| `docs/agentcenter/target-capability-architecture.md` | Defines the intended AgentCenter capability model above OpenCode. |
| `docs/agentcenter/workspace-isolation-design.md` | Designs user/workspace/project directory ownership and future isolation. |
| `docs/agentcenter/controlled-runtime-project-resources-design.md` | Records the accepted direction that F7/F8 manage project-level Skill/MCP registries, while work item runtime workspaces consume generated effective resources. |

These docs currently establish direction only. They do not mean the underlying
service or data model exists.

## Not Implemented Yet

These items are still design targets, not code:

- AgentCenter user or tenant resolver.
- Product workspace registry.
- Server-side workspace-to-directory allowlist.
- Project/workspace Skill registry.
- Real Skill upload, validation, enable/disable, version snapshot, or refresh.
- Project/workspace MCP registry.
- MCP desired-state reconciler, health probe, start/stop, or secret reference.
- Workflow run ledger.
- Workflow state capsule for compaction/resume recovery.
- Artifact registry, upload durability, preview durability, or promotion.
- Full OpenCode native side panel tabs and review/context tooling beyond the
  first controlled `SessionReview` and file-to-prompt bridge.
- Native PromptInput deep hardening: complete command palette integration and
  remaining production raw-port lockdown outside the current AgentCenter dev
  proxy.
- Any old Java Bridge or Vue workbench behavior.

## Verification History

| Date | Change | Verification |
| --- | --- | --- |
| 2026-05-25 | Resource center mock | `bun typecheck` in `packages/app`; `bun run build` in `packages/app`; Playwright smoke opened `/agentcenter/resources` and switched Skill/MCP tabs. |
| 2026-05-25 | AgentCenter workbench shell | `bun typecheck` in `packages/app`; `bun run build` in `packages/app`; `git diff --check`; Playwright smoke opened `/agentcenter/workbench`, switched detail/confirmation/artifact/resource tabs, captured desktop/mobile screenshots, and navigated from `/agentcenter/resources` back to the workbench. |
| 2026-05-26 | Shell/API closure pass | `bun typecheck` in `packages/app`; `bun run build` in `packages/app`; `git diff --check`; Playwright smoke verified shared shell navigation, disabled workflow entry, workbench resource tab, resource status filters, and mobile navigation. |
| 2026-05-26 | Frontend boundary correction | `bun typecheck` in `packages/app`; `bun run build` in `packages/app`; `git diff --check`; `rg` check found no AgentCenter workbench/resource routes or pages under `packages/app/src`. Added separate `packages/agentcenter-web/` placeholder. |
| 2026-05-26 | Separate AgentCenter display shell | `bun typecheck` in `packages/agentcenter-web`; `bun run build` in `packages/agentcenter-web`; `git diff --check`; Playwright smoke opened `http://127.0.0.1:5175/`, switched home/board/workflow, selected a work item, opened artifact preview, and captured desktop/mobile screenshots. |
| 2026-05-26 | Workspace-control conversation stream | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/agentcenter-web build`; curl verified `/agentcenter/session/:id/bootstrap` returns controlled messages/status/diff; in-app browser smoke opened `http://127.0.0.1:5175/`, opened a controlled work item, sent `test`, observed the OpenCode reply through `SessionTurn`, confirmed `idle` status and no visible `step-start`/`step-finish` parts. |
| 2026-05-27 | Native conversation docks | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; curl verified `/agentcenter/me`, OpenAPI AgentCenter paths including `abort/revert/unrevert/question/permission`, controlled open work item, and bootstrap now returns `messages/status/diff/questions/permissions`; Chrome smoke opened `http://127.0.0.1:5174/` and opened a controlled work item with the right-side scope and file changes panel visible. |
| 2026-05-27 | Controlled file panel | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; curl verified controlled `file`, `file/content`, `find/file`, and path escape denial through `/agentcenter/session/:id/...`; Playwright smoke opened `http://127.0.0.1:5174/`, opened WI-001, expanded `src`, selected `sample.ts`, and captured `.sisyphus/evidence/agentcenter-controlled-file-panel-preview.png`. |
| 2026-05-27 | Controlled native session commands | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; curl verified controlled open, bootstrap `session`, forked session binding/scope, and expected 400 for compact on an empty session without model context. |
| 2026-05-27 | Controlled native prompt protocol | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; curl verified `/agentcenter/session/:id/message` accepts native `PromptInput.parts` with `noReply: true`, stores a native text part visible from bootstrap, and still accepts the compatibility text-only payload. |
| 2026-05-27 | Native PromptInput adapter | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/agentcenter-web build`; `bun test test/workspace-control` from `packages/opencode`; Playwright smoke opened `http://127.0.0.1:5174/`, opened WI-001, confirmed the original OpenCode `PromptInput` textbox rendered, route moved to the controlled session URL, and browser console had no errors. |
| 2026-05-27 | Workspace route token and session restore | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/agentcenter-web build`; `bun test test/workspace-control` from `packages/opencode`; `git diff --check`; Playwright smoke opened WI-001 at `http://127.0.0.1:5174/`, confirmed URL uses `w.wc_.../session/:id` without native path leakage, original `PromptInput` renders, a fresh page can restore the same route through `/agentcenter/session/:id/scope`, raw `/session` is not proxied as OpenCode JSON, and console/request failures are clean. |
| 2026-05-27 | Workspace-control dev proxy guard | `bun --cwd packages/agentcenter-web typecheck`; Playwright smoke reopened WI-001 at `http://127.0.0.1:5174/`, confirmed original `PromptInput` mounts only after the `w.<workspaceId>/session/:id` route is ready, a fresh route restore works, direct `/config?directory=/tmp/outside-agentcenter` returns 403 JSON from the workspace-control guard, and raw `/session/:id?directory=...` still resolves to Vite HTML rather than OpenCode JSON. Screenshot: `.sisyphus/evidence/agentcenter-native-prompt-workspace-route.png`. |
| 2026-05-27 | Controlled native read compatibility | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; Playwright request capture opened WI-001 and interacted with `/` and `@s` in the original PromptInput; verified config/provider/path/project, agent/command/LSP/MCP/VCS/question/permission reads all went through `/agentcenter/session/:id/native/*` with 200 responses and no browser network request contained raw `?directory=`. |
| 2026-05-27 | Controlled event stream and opaque workspace token | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun test test/workspace-control` from `packages/opencode`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; Playwright smoke opened WI-001, confirmed original PromptInput rendered, captured 51 native reads through `/agentcenter/session/:id/native/*` including provider/agent render context, confirmed session events use `/agentcenter/session/:id/event`, verified scope/bootstrap/native path/event payloads contain `workspaceId` instead of host paths, and verified raw `/config?directory=/tmp/outside-agentcenter` plus raw `/global/event` return 403. Screenshot: `.sisyphus/evidence/agentcenter-controlled-event-opaque-workspace.png`. |
| 2026-05-27 | Native file context bridge | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun test test/workspace-control` from `packages/opencode`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; Playwright smoke opened WI-001, expanded `src/sample.ts`, used the controlled file panel to add it to OpenCode's original PromptInput context list, posted a native `PromptInput.parts` payload with `file://<workspaceId>/src/sample.ts`, verified the controlled `/message` route returned 200, confirmed raw `/config?directory=/tmp/outside-agentcenter` and raw `/global/event` return 403, and found no runtime path leaks in non-Vite module requests. Screenshot: `.sisyphus/evidence/agentcenter-native-file-context-bridge.png`. |
| 2026-05-27 | Native file selection context | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun test test/workspace-control` from `packages/opencode`; `bun --cwd packages/agentcenter-web build`; Playwright smoke opened WI-001, expanded `src/sample.ts`, selected line 1 in the OpenCode UI file viewer, added the selection to the original PromptInput context list as `sample.ts:1`, posted a native file part with `file://<workspaceId>/src/sample.ts?start=1&end=1`, verified `/agentcenter/session/:id/message` returned 200, confirmed raw `/config?directory=/tmp/outside-agentcenter` and raw `/global/event` return 403, and found no runtime path leaks in non-Vite module requests. Screenshot: `.sisyphus/evidence/agentcenter-native-file-selection-context.png`. |
| 2026-05-27 | Native SessionReview bridge | `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/opencode typecheck`; `bun test test/workspace-control` from `packages/opencode`; `bun --cwd packages/agentcenter-web build`; Browser smoke opened `http://127.0.0.1:5174/`, opened WI-001, confirmed the file panel stays bound to workspace-control file routes. The active test session had no `session.diff`, so native `SessionReview` visibility is gated until OpenCode emits a real diff event; type/build verification covers the embedded component and prompt-context bridge. |
| 2026-05-27 | Message file open bridge and responsive file panel | `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/opencode typecheck`; `bun test test/workspace-control` from `packages/opencode`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; Browser smoke at 904px width confirmed the workspace-control panel remains visible below the chat, expanded `src`, opened `sample.ts`, and saw `src/sample.ts` plus `Add context` in the controlled panel. Type/build verification covers message file target extraction and the controlled file-open event bridge. |
| 2026-05-27 | Controlled command file-part guard | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun test test/workspace-control` from `packages/opencode`; command payload file parts now reuse the workspace-control opaque file URL rewrite and path-escape rejection before calling OpenCode native command handling. |
| 2026-05-27 | Native SSE part ordering buffer | `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/opencode typecheck`; `bun test test/workspace-control` from `packages/opencode`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; Browser smoke reloaded `http://127.0.0.1:5174/`, restored a controlled `w.<workspaceId>/session/:id` route, verified native PromptInput, workspace-control panel, controlled file panel, realtime status, and no browser console errors. |
| 2026-05-27 | Controlled snapshot path scrub | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun test test/workspace-control` from `packages/opencode`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; controlled bootstrap/message snapshot responses now scrub server-side `allowedRoot` strings to the opaque `workspaceId`, matching the controlled event stream. |
| 2026-05-27 | Native PromptInput session slash commands | `bun --cwd packages/agentcenter-web typecheck`; `bun --cwd packages/opencode typecheck`; Browser smoke posted a no-reply controlled message, typed `/comp`, `/fork`, and `/undo` in the original OpenCode PromptInput, and verified the native slash popover includes `session.compact`, `session.fork`, and `session.undo` with no console errors. Native `session.summarize` requests now map to the controlled compact route. |
| 2026-05-27 | Native todo/children session reads | `bun --cwd packages/opencode typecheck`; `bun --cwd packages/agentcenter-web typecheck`; `bun test test/workspace-control` from `packages/opencode`; `bun --cwd packages/agentcenter-web build`; `git diff --check`; restarted local OpenCode serve on 4096, opened a fresh controlled WI-001 session, verified `/agentcenter/session/:id/todo` and `/children` return 200 arrays, bootstrap returns `todos: []`, the browser route uses `w.<workspaceId>/session/:id`, original PromptInput renders, and the session event stream reaches `实时连接`. |
| 2026-05-27 | Controlled native session/message mutations | `bun typecheck` in `packages/opencode`; `bun typecheck` in `packages/agentcenter-web`; `bun test test/workspace-control` from `packages/opencode`; `bun run build` in `packages/agentcenter-web`; `git diff --check`; 5174 proxy smoke opened a controlled WI-001 session, posted native no-reply prompt parts, verified controlled single-message read, session title update, part update, part delete, message delete, and confirmed raw `/config?directory=/tmp/outside-agentcenter` plus raw `/global/event` return 403. Browser smoke reopened WI-001, confirmed the route uses `w.<workspaceId>/session/:id`, original `PromptInput` is mounted, workspace token is visible, realtime status is connected, and console errors are empty. Screenshot: `.sisyphus/evidence/agentcenter-native-workspace-control-smoke.png`. |

Known verification note:

- Root `bun install --frozen-lockfile` reached the Electron postinstall step and
  failed once with a network socket hang up, but enough dependencies were
  present for the app typecheck/build/smoke verification to pass.

## Next Decision Needed

No workflow implementation decision is needed for the current closure pass. The
next meaningful decision is how the real backend boundary should expose project
Skill/MCP registries and work item runtime workspace allocation.
