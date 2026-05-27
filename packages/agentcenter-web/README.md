# AgentCenter Web

Status: standalone AgentCenter shell backed by OpenCode native runtime APIs.

Boundary:

- `packages/app` remains the upstream OpenCode Web application.
- AgentCenter display work should live here, not inside OpenCode's Web shell.
- The first runtime target is a controlled project/work-item/user workspace
  flow through `workspace-control`.
- Conversation rendering reuses OpenCode UI's native `SessionTurn` /
  `message-part` components for OpenCode `message + parts` payloads instead of
  inventing a separate chat protocol or custom part cards. The shell also
  loads native provider and agent context through controlled routes so model
  labels and task/agent tool cards have the same supporting data as OpenCode
  Web.
- Conversation state is driven by OpenCode SSE events for
  `message.updated`, `message.part.updated`, `message.part.delta`,
  `message.part.removed`, `message.removed`, `session.status`, and
  `session.diff`, plus native `question.*` and `permission.*` events for
  composer docks. The shell subscribes through the controlled
  `/agentcenter/session/:id/event` stream, bootstraps through
  `/agentcenter/session/:id/bootstrap`, and does not poll `/message` as a
  rendering fallback. OpenCode uses `message.updated` and
  `message.part.updated` for both create and update semantics; the shell
  buffers part/delta events that arrive before their parent message so native
  turn rendering does not depend on a later refresh.
- Native question and permission docks are copied/adapted from OpenCode Web's
  composer dock. They call controlled AgentCenter routes rather than raw
  directory-routed OpenCode endpoints.
- The shell mounts OpenCode Web's original `PromptInput` component for the
  composer. A small workspace-control fetch adapter routes native
  session-list/session-get/message/diff/todo/children, native
  config/provider/path/project, native agent/command/LSP/MCP/VCS/question/permission reads,
  prompt/command/shell/abort, and file-search calls through the bound
  `/agentcenter/session/:id/...` APIs instead of trusting a browser-selected
  directory.
- Native session metadata and message/part mutations are also intercepted by
  the adapter. Session title/archive updates, share/unshare, single-message
  reads, message delete, part delete, and part update use controlled
  `/agentcenter/session/:id/...` routes rather than raw OpenCode session
  routes.
- A narrow command bridge registers core OpenCode session slash commands into
  the original `PromptInput` command registry. `/undo`, `/redo`, `/compact`,
  and `/fork` appear through OpenCode's native slash popover and execute via
  workspace-control callbacks rather than raw directory-routed OpenCode APIs.
  Native `session.summarize` requests are also translated to the controlled
  compact route.
- Native todo state is also routed through workspace-control. The embedded
  OpenCode sync layer calls controlled `/agentcenter/session/:id/todo`, listens
  to `todo.updated` on the session-scoped event stream, and renders OpenCode
  Web's original `SessionTodoDock` instead of a custom AgentCenter todo card.
- The right-panel file viewer can add a file to the original `PromptInput`
  context list through a small bridge to OpenCode's native `PromptProvider`.
  Text previews also enable OpenCode UI line selection, so selected ranges can
  be added as native context items like `sample.ts:1`. The prompt request still
  uses OpenCode's native file-part contract; the server rewrites opaque
  `file://<workspaceId>/...?start=&end=` references to the bound `allowedRoot`
  and rejects path escapes.
- Changed-file review uses OpenCode UI's native `SessionReview` component for
  real `session.diff` payloads. Diff style switching, accordion behavior,
  file-open action, line selection, and review line-comment submission are
  handled by the native component; AgentCenter only routes file reads and
  prompt context updates through the controlled session.
- File references rendered inside native conversation turns can route to the
  controlled right-side file panel. This currently covers inline file
  references, user file attachments, tool file headers, and turn diff file
  names; the panel still resolves content only through the bound
  `/agentcenter/session/:id/file/content` API.
- The controlled `/agentcenter/session/:id/message` route accepts OpenCode
  native `PromptInput.parts` payloads. The server still accepts the older
  text-only payload temporarily for compatibility.
- Controlled bootstrap/message snapshot responses scrub the server-side
  `allowedRoot` back to the opaque `workspaceId`, matching the event stream
  behavior when native file context parts are present.
- The shell exposes controlled stop/revert/unrevert/fork/compact actions
  through `/agentcenter/session/:id/abort`, `/revert`, `/unrevert`, `/fork`,
  and `/compact`. The backend also exposes controlled `/command` and `/shell`
  routes for the next PromptInput migration step.
- The shell includes a controlled right-panel file experience backed by
  `/agentcenter/session/:id/file`, `/file/content`, and `/find/file`.
  It reuses OpenCode UI `SessionReview`, `File`, and `FileIcon` primitives for
  native diff review and text preview.
- The browser never chooses an OpenCode directory. It opens a product
  project/work-item session through `workspace-control`, and all session
  bootstrap/message/file calls are resolved server-side from that binding.
- Browser routes use `w.<workspaceId>/session/:id`, not base64-encoded host
  directories. The embedded native OpenCode contexts use the opaque
  `workspaceId` as their client-side directory key; the real `allowedRoot`
  stays in the server-side session binding.
- Raw `/global/event` is not exposed to the browser. The embedded OpenCode
  global-sync layer is pointed at a virtual workspace-control server identity,
  and the adapter rewrites its event request to
  `/agentcenter/session/:id/event`.
- Full OpenCode side panel tabs, production hardening for opaque directory
  tokens, Skill/MCP management, and workflow UI remain later explicit
  integrations.

Do not add routes or pages under `packages/app/src/pages/agentcenter-*`.

## Scripts

```bash
bun --cwd packages/agentcenter-web dev
bun --cwd packages/agentcenter-web typecheck
bun --cwd packages/agentcenter-web build
```

The local scripts currently reuse the existing Vite binary from
`packages/app/node_modules` to keep this package lightweight.

The dev server proxies `/agentcenter/*`, `/global/*`, and a small set of raw
OpenCode routes that do not carry a workspace directory. Raw session/file/find
routes are intentionally not proxied; raw `/global/event` is also blocked so
events must go through the session-scoped workspace-control stream.

The embedded `PromptInput` no longer sends raw directory-routed native reads
such as `/config?directory=...` from the browser. Those reads are rewritten to
controlled routes like `/agentcenter/session/:id/native/config`, where the
OpenCode server resolves the bound `allowedRoot` from the session binding. The
Vite proxy rejects direct raw directory routes as a local guard. Production must
still isolate the OpenCode server behind the AgentCenter gateway so browsers
cannot talk to the raw OpenCode port directly.

Default target:

```bash
http://127.0.0.1:4096
```

Override when needed:

```bash
AGENTCENTER_OPENCODE_URL=http://127.0.0.1:4097 bun --cwd packages/agentcenter-web dev
```
