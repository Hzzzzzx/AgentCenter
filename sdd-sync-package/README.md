# Agent / SDD Sync Package

这个目录是从当前机器整理出来的可同步版 Agent / SDD 规则包，用于迁移到另一台机器。项目级内容已按 `~/workspace/TianYuan` 重新整理。

## 包内容

- `global/agent-rules/AGENTS.md`：当前全局轻量 Agent 规则源文件。
- `global/opencode/AGENTS.md`：OpenCode 全局 Agent 规则副本。
- `global/claude/CLAUDE.md`：Claude 全局补充规则。
- `project/TianYuan/AGENTS.md`：TianYuan 项目级 SDD v.next 行为合同。
- `project/TianYuan/CLAUDE.md`：TianYuan 项目级 Claude/GitNexus 补充规则。
- `project/TianYuan/docs/sdd/`：TianYuan SDD 规范和 Blueprint Graph 文档。
- `project/TianYuan/docs/reference/sdd-*.md`：SDD 改进、评价和横向对比参考。
- `project/TianYuan/.opencode/`：项目级 Agent prompt 和 OpenCode skills。
- `project/TianYuan/.sisyphus/`：只包含 SDD/Blueprint 相关模板、草稿、计划和问题清单。
- `reports/`：源文件映射、敏感信息扫描和校验和。

## 建议同步位置

在目标机器上，可按需放置：

```bash
# 全局规则
mkdir -p "$HOME/.config/agent-rules" "$HOME/.config/opencode" "$HOME/.claude"
cp global/agent-rules/AGENTS.md "$HOME/.config/agent-rules/AGENTS.md"
cp global/opencode/AGENTS.md "$HOME/.config/opencode/AGENTS.md"
cp global/claude/CLAUDE.md "$HOME/.claude/CLAUDE.md"

# 如果目标机器使用 Codex，并希望沿用当前布局
mkdir -p "$HOME/.codex"
ln -sf "$HOME/.config/agent-rules/AGENTS.md" "$HOME/.codex/AGENTS.md"
```

项目级文档复制到目标机器的 TianYuan 仓库根目录即可：

```bash
cp -R project/TianYuan/. "$HOME/workspace/TianYuan/"
```

## 脱敏处理

已将本机绝对用户路径统一替换为 `$HOME/...`。发现并脱敏了 TianYuan 项目级 `.opencode` skill 中的 Z AI API key，占位符为 `<REDACTED_Z_AI_API_KEY>`。

同步前请查看 `reports/sensitive-scan.md`，其中列出了需要你确认是否保留或重新配置的内容。
