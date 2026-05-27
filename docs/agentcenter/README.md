# AgentCenter Design Notes

AgentCenter in this fork is designed as an OpenCode-native product extension.
The old AgentCenter repository is requirement reference only, not the target
implementation base.

## Documents

- `opencode-native-branch-tracker.md`: tracks what has actually changed on the
  OpenCode-native branch after the baseline import, including committed mocks,
  planning-only docs, and user feedback.
- `1026-frontend-feature-cards.md`: breaks the 1026 Vue frontend into
  page-level feature cards so the team can decide which UI shells and function
  points should be kept, redesigned, deferred, or dropped.
- `1026-feature-adaptation-plan.md`: maps useful capabilities from the old
  `codex/from-2026-05-14-1026` branch into this OpenCode-native branch, including
  what to keep, translate, defer, or drop.
- `target-capability-architecture.md`: overall capability plan for user
  isolation, project/workspace Skill, project/workspace MCP, upload/artifact
  durability, and workflow continuity.
- `workspace-isolation-design.md`: lower-level design for mapping product
  workspaces to controlled OpenCode worktree directories.
- `controlled-runtime-project-resources-design.md`: focused design for the
  controlled runtime root, project-level Skill/MCP registries, and work item
  runtime workspace projection.
- `workspace-control-design.md`: names the control-plane module
  `workspace-control` and defines its responsibility for identity, project and
  work item registry, runtime directory resolution, execution lease, session
  binding, collaboration, and agent access boundaries.

## Current Frontend Boundary

- `packages/app` is OpenCode's own Web application and should stay upstream
  shaped.
- AgentCenter display work belongs in `packages/agentcenter-web/`.
- `packages/agentcenter-web` now contains the standalone AgentCenter shell. It
  uses seeded local project/work item data, opens sessions through
  `workspace-control`, and renders OpenCode conversations with native OpenCode
  UI components.
- The next frontend target is to keep moving native OpenCode interaction
  surfaces into the AgentCenter shell without letting the browser choose raw
  directories.

## Current Runtime Boundary

- The runtime control module is named `workspace-control`.
- `workspace-control` maps `{ tenantId, projectId, workItemId, userId }` to a
  controlled OpenCode runtime directory.
- Shared collaboration state lives at the work item level.
- OpenCode execution runs in per-user work item workspaces.
- Agents must only access the current runtime scope's allowed root unless a
  later reviewed sharing or promotion flow grants access.
- Controlled AgentCenter routes now cover session open/bootstrap/message/file,
  question/permission docks, abort/revert/unrevert, fork, and compact.
