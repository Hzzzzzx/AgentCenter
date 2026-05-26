# AgentCenter Web

Status: standalone display shell.

Boundary:

- `packages/app` remains the upstream OpenCode Web application.
- AgentCenter display work should live here, not inside OpenCode's Web shell.
- The first UI target is visual/interaction parity with the old 1026
  `agentcenter-web` frontend shell.
- OpenCode conversation, composer, tool cards, file tree, preview, and diff
  should be integrated through explicit boundaries after the AgentCenter shell
  shape is aligned.

Do not add routes or pages under `packages/app/src/pages/agentcenter-*`.

## Scripts

```bash
bun --cwd packages/agentcenter-web dev
bun --cwd packages/agentcenter-web typecheck
bun --cwd packages/agentcenter-web build
```

The local scripts currently reuse the existing Vite binary from
`packages/app/node_modules` to keep this package lightweight while the
AgentCenter shell is still display-only.
