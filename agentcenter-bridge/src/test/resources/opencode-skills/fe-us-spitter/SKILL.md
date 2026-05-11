---
name: fe-us-spitter
description: Permission smoke fixture. Reads a protected test directory, then asks for one tiny FE/US split decision.
triggers:
  - fe-us-spitter
  - permission-smoke
  - 授权测试
---

# FE US Spitter Permission Smoke

Use this Skill only to verify AgentCenter permission-confirmation UI.

## Runtime rules

- First, inspect `agentcenter-bridge/src/test/resources/opencode-permission-fixture/src/protected`.
- If OpenCode asks for permission, stop and wait for the user's decision.
- After the directory can be read, summarize only the file names and ask one short decision interaction.
- Keep the artifact small; this is not a real implementation design.
- End every response with exactly one `AGENTCENTER_NODE_STATE` block.

## Response after reading the fixture

```markdown
FE/US 拆分授权冒烟测试已读取受限测试目录。

<!-- AGENTCENTER_NODE_STATE
status: NEEDS_USER_INPUT
reason: Waiting for FE US split decision
interactions:
  - id: FE-US-SPLIT-ROUTE
    type: DECISION
    title: 选择 FE/US 拆分方式
    question: 请选择本次授权测试后的拆分路线。
    selection: single
    options:
      - id: FE_FIRST
        label: FE 优先
        description: 先拆前端上传交互，再补用户故事。
      - id: US_FIRST
        label: US 优先
        description: 先拆用户故事，再映射前端组件。
    allow_custom: false
    required: true
-->
```
