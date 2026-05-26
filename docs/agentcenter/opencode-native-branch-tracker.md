# OpenCode Native Branch Tracker

Status: active tracking document
Branch: `codex/opencode-native-base`
Remote: `origin/codex/opencode-native-base`
Last updated: 2026-05-26

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
- OpenCode conversation, composer, tool cards, file tree, file preview, and diff
  should be integrated later through explicit boundaries.
- Workflow implementation remains deferred.

Current implementation files:

| File | Purpose |
| --- | --- |
| `packages/agentcenter-web/` | Adds the separate AgentCenter frontend package with a 1026-style display shell, demo work item data, board, right panel, artifact preview, and deferred workflow placeholder. |
| `packages/agentcenter-web/README.md` | Defines the new separate frontend boundary for AgentCenter display work and local scripts. |
| `packages/app/src/app.tsx` | Removes AgentCenter routes from OpenCode Web. |
| `packages/app/src/pages/home.tsx` | Removes the AgentCenter/ability-center entry from OpenCode Home. |
| `docs/agentcenter/1026-feature-adaptation-plan.md` | Adds P0.5 resource center mock phase. |

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
- OpenCode session creation through AgentCenter workspace ids.
- Any old Java Bridge or Vue workbench behavior.

## Verification History

| Date | Change | Verification |
| --- | --- | --- |
| 2026-05-25 | Resource center mock | `bun typecheck` in `packages/app`; `bun run build` in `packages/app`; Playwright smoke opened `/agentcenter/resources` and switched Skill/MCP tabs. |
| 2026-05-25 | AgentCenter workbench shell | `bun typecheck` in `packages/app`; `bun run build` in `packages/app`; `git diff --check`; Playwright smoke opened `/agentcenter/workbench`, switched detail/confirmation/artifact/resource tabs, captured desktop/mobile screenshots, and navigated from `/agentcenter/resources` back to the workbench. |
| 2026-05-26 | Shell/API closure pass | `bun typecheck` in `packages/app`; `bun run build` in `packages/app`; `git diff --check`; Playwright smoke verified shared shell navigation, disabled workflow entry, workbench resource tab, resource status filters, and mobile navigation. |
| 2026-05-26 | Frontend boundary correction | `bun typecheck` in `packages/app`; `bun run build` in `packages/app`; `git diff --check`; `rg` check found no AgentCenter workbench/resource routes or pages under `packages/app/src`. Added separate `packages/agentcenter-web/` placeholder. |
| 2026-05-26 | Separate AgentCenter display shell | `bun typecheck` in `packages/agentcenter-web`; `bun run build` in `packages/agentcenter-web`; `git diff --check`; Playwright smoke opened `http://127.0.0.1:5175/`, switched home/board/workflow, selected a work item, opened artifact preview, and captured desktop/mobile screenshots. |

Known verification note:

- Root `bun install --frozen-lockfile` reached the Electron postinstall step and
  failed once with a network socket hang up, but enough dependencies were
  present for the app typecheck/build/smoke verification to pass.

## Next Decision Needed

No workflow implementation decision is needed for the current closure pass. The
next meaningful decision is how the real backend boundary should expose project
Skill/MCP registries and work item runtime workspace allocation.
