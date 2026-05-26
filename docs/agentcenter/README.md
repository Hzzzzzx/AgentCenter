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

## Current Frontend Boundary

- `packages/app` is OpenCode's own Web application and should stay upstream
  shaped.
- AgentCenter display work belongs in `packages/agentcenter-web/`.
- `packages/agentcenter-web` now contains the first standalone 1026-style
  display shell. It uses demo work item data only.
- The next frontend target is to tighten visual parity and then integrate
  OpenCode capabilities through explicit boundaries.
