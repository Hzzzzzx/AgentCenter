# OpenCode Native Branch Tracker

Status: active tracking document
Branch: `codex/opencode-native-base`
Remote: `origin/codex/opencode-native-base`
Last updated: 2026-05-25

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
| `b163c349` | app mock | `packages/app/src/app.tsx`, `packages/app/src/pages/home.tsx`, `packages/app/src/pages/agentcenter-resource-center.tsx`, `docs/agentcenter/1026-feature-adaptation-plan.md` | Added a visible `能力中心` entry and mock Skill/MCP management page. | Needs redesign review |

## Current Implemented Delta

Only one product-facing implementation exists today:

- Home left navigation has an `能力中心` entry.
- `/agentcenter/resources` route renders a new OpenCode-native Solid page.
- The page shows mock Skill and MCP lists, metrics, status pills, and a detail
  panel.
- The status vocabulary in the mock page is `normal`, `warning`, `failed`, and
  `disabled`.
- Upload, refresh, new MCP, start, stop, delete, and health checks are not
  wired to runtime behavior.

Current implementation files:

| File | Purpose |
| --- | --- |
| `packages/app/src/pages/agentcenter-resource-center.tsx` | Mock Skill/MCP resource center UI. |
| `packages/app/src/app.tsx` | Adds `/agentcenter/resources` route. |
| `packages/app/src/pages/home.tsx` | Adds home navigation entry. |
| `docs/agentcenter/1026-feature-adaptation-plan.md` | Adds P0.5 resource center mock phase. |

## User Feedback On Current Mock

On 2026-05-25, after seeing the page, the user said this was not the intended
result. Treat the current `能力中心` mock as a traceable prototype, not as a
confirmed target UI.

Implication:

- Do not build backend registry APIs around this exact UI yet.
- Revisit the product shape before continuing Skill/MCP implementation.
- Keep the commit documented so it can be adjusted, replaced, or reverted
  deliberately.

## Planning-Only Delta

The following docs were added or updated, but they do not change runtime
behavior:

| Document | Purpose |
| --- | --- |
| `docs/agentcenter/1026-feature-adaptation-plan.md` | Maps old 1026 AgentCenter capabilities into OpenCode-native decisions. |
| `docs/agentcenter/target-capability-architecture.md` | Defines the intended AgentCenter capability model above OpenCode. |
| `docs/agentcenter/workspace-isolation-design.md` | Designs user/workspace/project directory ownership and future isolation. |

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

Known verification note:

- Root `bun install --frozen-lockfile` reached the Electron postinstall step and
  failed once with a network socket hang up, but enough dependencies were
  present for the app typecheck/build/smoke verification to pass.

## Next Decision Needed

Before adding real Skill/MCP backend behavior, decide what the first accepted
visible shape should be:

- keep a central `能力中心` page and redesign it;
- move Skill/MCP management into project/workspace settings;
- keep only a small project resource panel first;
- or remove the mock page and continue with invisible workspace registry
  foundation.
