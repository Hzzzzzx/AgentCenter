---
name: fe-us-spitter
description: Permission smoke fixture. Reads an external protected test directory to trigger OpenCode authorization.
triggers:
  - fe-us-spitter
  - permission-smoke
  - 授权测试
---

# FE US Spitter Permission Smoke

Use this Skill only to verify AgentCenter permission-confirmation UI.

## Runtime rules

- First, inspect `/Users/hzz/workspace/AgentCenter/agentcenter-bridge/src/test/resources/opencode-permission-fixture/src/protected`.
- This path is intentionally outside the OpenCode runtime worktree; do not use any copy under `runtime-workspace`.
- If OpenCode asks for permission, stop and wait for the user's decision before continuing.
- After the directory can be read, summarize only the file names.
- Do not ask a FE/US split question, do not emit DECISION interactions, and do not request business confirmation.
- Keep the artifact small; this is only an OpenCode authorization smoke test.
- End every response with exactly one `AGENTCENTER_NODE_STATE` block that marks this node complete.

## Response after reading the fixture

```markdown
OpenCode 外部目录授权冒烟测试已读取受限测试目录。

<!-- AGENTCENTER_NODE_STATE
status: READY_TO_ADVANCE
reason: External directory authorization smoke completed
interactions: []
-->
```
