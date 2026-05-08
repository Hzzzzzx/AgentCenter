---
name: agentcenter-workspace-smoke
description: Verify AgentCenter runtime working-directory routing and workspace-local context.
triggers:
  - agentcenter-workspace-smoke
  - workspace smoke
  - 工作目录验证
---

# AgentCenter Workspace Smoke

Use this skill to verify that the current runtime is operating in the expected project workspace.

## Required Checks

1. Read `README.md`.
2. Read `src/sample-feature.md`.
3. If MCP tools are available, call the workspace marker tool from `agentcenter-test-workspace`.

## Output

Return a Markdown report with:

- Current working-directory evidence.
- Whether `AGENTCENTER_RUNTIME_TEST_WORKSPACE=runtime-test-workspace` was found.
- Whether `src/sample-feature.md` was found.
- Whether the test MCP marker was available.
- A final verdict: `PASS` or `FAIL`.

