# Sensitive Scan

## Summary

- 发现 TianYuan 项目级 `.opencode` skills 中存在真实 Z AI API key，已在同步包内脱敏为 `<REDACTED_Z_AI_API_KEY>`。
- 未发现未脱敏的 OpenAI/GitHub/AWS token、私钥、bearer token 或 Codex auth 文件内容。
- 已自动脱敏本机绝对用户路径：`/Users/<local-user>` -> `$HOME`。
- 未复制运行态敏感文件：Codex auth/config、SQLite 日志库、session/transcript、worktree、缓存、TianYuan `.sisyphus/evidence` 和 `.sisyphus/opencode-runs`。

## Auto-Handled

| Pattern | Handling |
|---|---|
| 本机绝对路径 `/Users/<local-user>/...` | 替换为 `$HOME/...` |
| `.opencode/skills/media-analysis/SKILL.md` 的 `Z_AI_API_KEY` | 替换为 `<REDACTED_Z_AI_API_KEY>` |
| `.opencode/skills/search-tools/SKILL.md` 的 `Authorization` header | 替换为 `<REDACTED_Z_AI_API_KEY>` |
| Codex symlink source path | 记录为 `$HOME/.config/agent-rules/AGENTS.md` |
| OpenCode / Claude home paths | 保留为 `$HOME` 或 `~` 形式 |

## Findings That Look Safe

这些命中来自示例、术语或协议说明，不是实际凭据：

- 文档中出现 `secrets`、`token`、`password` 等治理术语。
- SDD 安全扫描文档中包含 webhook/JWT/token 的正则样例。
- 本地开发端口和地址，如 `localhost`、`127.0.0.1`、TianYuan Vite/Tauri 端口。
- GitNexus、graphify、Blueprint Graph、OpenCode 等项目工具名。

## Needs Your Confirmation

同步到另一台机器前，建议你确认：

1. **是否保留项目级 OpenCode skills**：我已脱敏 key，但目标机器需要重新配置 Z AI API key，否则 `media-analysis` / `search-tools` 不能直接用。
2. **是否保留 `.sisyphus` SDD 草稿/计划**：当前只带 SDD/Blueprint 相关模板、draft、plan 和 checklist，没有带 evidence/run/archive。
3. **是否保留内部项目名和工具名**：`TianYuan`、`GitNexus`、`graphify`、`Blueprint Graph`、`OpenCode` 等。

当前包按“同一用户在另一台机器继续 TianYuan 开发”的场景保留了这些信息。
