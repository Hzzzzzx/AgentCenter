# Source Map

## Global

| Package file | Source |
|---|---|
| `global/agent-rules/AGENTS.md` | `$HOME/.config/agent-rules/AGENTS.md` |
| `global/opencode/AGENTS.md` | `$HOME/.config/opencode/AGENTS.md` |
| `global/claude/CLAUDE.md` | `$HOME/.claude/CLAUDE.md` |

`$HOME/.codex/AGENTS.md` 在当前机器上是指向 `$HOME/.config/agent-rules/AGENTS.md` 的 symlink，因此没有重复复制。

## TianYuan

| Package file | Source |
|---|---|
| `project/TianYuan/AGENTS.md` | `TianYuan/AGENTS.md` |
| `project/TianYuan/CLAUDE.md` | `TianYuan/CLAUDE.md` |
| `project/TianYuan/README.md` | `TianYuan/README.md` |
| `project/TianYuan/docs/index.md` | `TianYuan/docs/index.md` |
| `project/TianYuan/docs/CONTRIBUTING.md` | `TianYuan/docs/CONTRIBUTING.md` |
| `project/TianYuan/docs/design-doc-template.md` | `TianYuan/docs/design-doc-template.md` |
| `project/TianYuan/docs/worktree-protocol.md` | `TianYuan/docs/worktree-protocol.md` |
| `project/TianYuan/docs/sdd/*.md` | `TianYuan/docs/sdd/*.md` |
| `project/TianYuan/docs/reference/sdd-*.md` | `TianYuan/docs/reference/sdd-*.md` |
| `project/TianYuan/docs/reference/knowledge-graph-tools-survey.md` | `TianYuan/docs/reference/knowledge-graph-tools-survey.md` |
| `project/TianYuan/scripts/blueprint-graph/README.md` | `TianYuan/scripts/blueprint-graph/README.md` |
| `project/TianYuan/.opencode/prompts/tool-usage-guide.md` | `TianYuan/.opencode/prompts/tool-usage-guide.md` |
| `project/TianYuan/.opencode/skills/*/SKILL.md` | `TianYuan/.opencode/skills/*/SKILL.md` |
| `project/TianYuan/.sisyphus/templates/plan-template.md` | `TianYuan/.sisyphus/templates/plan-template.md` |
| `project/TianYuan/.sisyphus/drafts/sdd-*.md` | `TianYuan/.sisyphus/drafts/sdd-*.md` |
| `project/TianYuan/.sisyphus/plans/blueprint-graph-phase-one.md` | `TianYuan/.sisyphus/plans/blueprint-graph-phase-one.md` |
| `project/TianYuan/.sisyphus/plans/sdd-v1-1-blueprint-graph-integration.md` | `TianYuan/.sisyphus/plans/sdd-v1-1-blueprint-graph-integration.md` |
| `project/TianYuan/.sisyphus/sdd-issues-checklist.md` | `TianYuan/.sisyphus/sdd-issues-checklist.md` |
| `project/TianYuan/.sisyphus/cards/schema.json` | `TianYuan/.sisyphus/cards/schema.json` |

## Explicitly Excluded

- `$HOME/.codex/auth.json`
- `$HOME/.codex/config.toml`
- `$HOME/.codex/*.sqlite*`
- `$HOME/.codex/log*`
- `$HOME/.codex/archived_sessions/`
- `$HOME/.codex/sessions/`
- `$HOME/.codex/worktrees/`
- `$HOME/.codex/plugins/cache/`
- TianYuan `.opencode/node_modules/`
- TianYuan `.sisyphus/evidence/`
- TianYuan `.sisyphus/opencode-runs/`
- TianYuan `.sisyphus/archive/`
- TianYuan `.sisyphus/cards/*.json` except `schema.json`
- TianYuan `.sisyphus/boulder.json` and `.sisyphus/worktrees.json`
- TianYuan `node_modules/`, `target/`, `dist/`, `build/`
- Historical backups under `$HOME/.config/opencode/backup/`
