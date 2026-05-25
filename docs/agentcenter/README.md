# AgentCenter Design Notes

AgentCenter in this fork is designed as an OpenCode-native product extension.
The old AgentCenter repository is requirement reference only, not the target
implementation base.

## Documents

- `1026-feature-adaptation-plan.md`: maps useful capabilities from the old
  `codex/from-2026-05-14-1026` branch into this OpenCode-native branch, including
  what to keep, translate, defer, or drop.
- `target-capability-architecture.md`: overall capability plan for user
  isolation, project/workspace Skill, project/workspace MCP, upload/artifact
  durability, and workflow continuity.
- `workspace-isolation-design.md`: lower-level design for mapping product
  workspaces to controlled OpenCode worktree directories.
