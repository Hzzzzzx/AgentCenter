# M1 Runbook: OpenCode Bridge SSE+REST

> Status: **M1 Complete** — Real OpenCode AI conversation in the Vue web workbench
> Last Updated: 2026-05-06
> Architecture Decision: [ADR-001](./ADR-001-OPENCODE-BRIDGE-SSE-REST.md)
> Known Pitfalls: [AGENTS.md §已知陷阱](../../AGENTS.md)

## Prerequisites

| Dependency | Minimum Version | Check Command | Install |
|---|---|---|---|
| Java (JDK) | 17 | `java -version` | [Adoptium](https://adoptium.net/) or `brew install openjdk@17` |
| Node.js | 20 | `node --version` | [nvm](https://github.com/nvm-sh/nvm) recommended |
| npm | bundled with Node | `npm --version` | comes with Node |
| opencode CLI | 1.14+ | `opencode --version` | `npm install -g opencode-ai` |
| Git | any | `git --version` | system package manager |

> **Important**: `opencode` must be on `PATH` and logged in (`opencode auth`). The Bridge starts `opencode serve` automatically.

## Configuration

### Working Directory (the project root)

The Bridge needs to know where your project lives on disk. This is the directory that opencode will operate in (read files, run commands, etc).

**Default**: `application.yml` sets `working-directory: ${user.dir}/..`

This resolves to the parent of `agentcenter-bridge/` — i.e. the project root. If you run `./mvnw spring-boot:run` from `agentcenter-bridge/`, the working directory will be `/path/to/AgentCenter`.

**Override** (if your layout differs):

```yaml
# agentcenter-bridge/src/main/resources/application.yml
agentcenter:
  runtime:
    opencode:
      serve:
        working-directory: /absolute/path/to/your/project   # ← change this
```

Or via environment variable:

```bash
export AGENTCENTER_RUNTIME_OPENCODE_SERVE_WORKING_DIRECTORY=/my/project
./mvnw spring-boot:run
```

### Port Configuration

| Service | Default Port | Config Key | Override Env Var |
|---|---|---|---|
| opencode serve | 4097 | `agentcenter.runtime.opencode.serve.port` | `AGENTCENTER_RUNTIME_OPENCODE_SERVE_PORT` |
| Java Bridge | 8080 | `server.port` | `SERVER_PORT` |
| Vue Dev Server | 5173 | `vite.config.ts` | `--port` flag |

If you change the opencode serve port, update both `application.yml` and remember the Vue dev server proxies to Java Bridge (8080), not to opencode serve directly.

### opencode Serve: Auto-Start vs Manual

The Bridge **automatically starts `opencode serve`** on first use (when a message is sent). You do NOT need to start it manually.

However, if you prefer to run it manually (e.g., to see logs in a separate terminal):

```bash
opencode serve --hostname 127.0.0.1 --port 4097 --print-logs --log-level WARN
```

The Bridge will detect the existing process and reuse it (no port conflict).

## Start All Services

### Option A: Manual (3 terminals)

**Terminal 1 — opencode serve (optional, Bridge starts it automatically):**

```bash
# Only needed if you want to see opencode logs in real-time
opencode serve --hostname 127.0.0.1 --port 4097 --print-logs --log-level WARN
```

**Terminal 2 — Java Bridge:**

```bash
cd agentcenter-bridge
./mvnw spring-boot:run
```

Wait for: `Started AgentCenterBridgeApplication in X.XXX seconds`

**Terminal 3 — Vue Frontend:**

```bash
cd agentcenter-web
npm install          # first time only
npm run dev
```

Wait for: `Local: http://localhost:5173/`

### Option B: Single-command (background)

```bash
# From project root
# 1. Start opencode serve (optional)
nohup opencode serve --hostname 127.0.0.1 --port 4097 \
  --print-logs --log-level WARN > /tmp/opencode_serve.log 2>&1 &

# 2. Start Java Bridge
cd agentcenter-bridge
nohup ./mvnw spring-boot:run -q > /tmp/bridge.log 2>&1 &
cd ..

# 3. Start Vue dev
cd agentcenter-web
nohup npm run dev > /tmp/vite.log 2>&1 &
cd ..

# Wait for services
sleep 12
echo "opencode: $(curl -s http://127.0.0.1:4097/path -H 'x-opencode-directory: .' -o /dev/null -w '%{http_code}')"
echo "Bridge:   $(curl -s http://localhost:8080/api/agent-sessions -o /dev/null -w '%{http_code}')"
echo "Vue:      $(curl -s http://localhost:5173/ -o /dev/null -w '%{http_code}')"
```

## Verify E2E

### Browser Test

1. Open http://localhost:5173
2. Navigate to a work item → click into conversation workbench
3. Type a message → see real streaming AI response

### curl Test

```bash
# 1. Create a session
SESSION=$(curl -s -X POST http://localhost:8080/api/agent-sessions \
  -H "Content-Type: application/json" \
  -d '{"title":"E2E Test","runtimeType":"OPENCODE","workItemId":"test-run"}')
SID=$(echo "$SESSION" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Session: $SID"

# 2. Listen for SSE events (background)
timeout 40 curl -s -N "http://localhost:8080/api/agent-sessions/$SID/events" &
SSE_PID=$!
sleep 1

# 3. Send a message
curl -s -X POST "http://localhost:8080/api/agent-sessions/$SID/messages" \
  -H "Content-Type: application/json" \
  -d '{"content":"Say hello in one sentence"}'

# 4. Wait and check — you should see ASSISTANT_DELTA events
sleep 20
kill $SSE_PID 2>/dev/null
```

Expected: SSE stream contains `ASSISTANT_DELTA` events with the AI's reply.

## Troubleshooting

### `opencode serve exited with code 1`

**Cause**: Port conflict — another process is using 4097.

```bash
# Check what's on the port
lsof -i:4097

# Kill it and retry, or change the port in application.yml
```

### SSE returns 0 bytes (no events)

**Most likely cause**: The Bridge process was started with stale code. Rebuild:

```bash
cd agentcenter-bridge
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

Also check that opencode serve is running: `curl http://127.0.0.1:4097/path -H 'x-opencode-directory: .'`

### `Cannot find opencode executable`

```bash
# Install globally
npm install -g opencode-ai

# Verify
opencode --version

# If installed elsewhere, configure the path in application.yml:
# agentcenter.runtime.opencode.serve.command: /path/to/opencode
```

### Vue dev server API proxy fails (502)

The Vue dev server proxies `/api` to `localhost:8080`. Make sure the Java Bridge is running first.

```bash
# Check proxy target
curl http://localhost:8080/api/agent-sessions
```

### `Warning: OPENCODE_SERVER_PASSWORD is not set`

This is a non-fatal warning from opencode serve. For local development it's safe to ignore. For production, set the environment variable.

## Architecture Overview

```
Browser                    Vue Dev (5173)          Java Bridge (8080)        opencode serve (4097)
  │                            │                         │                         │
  │  POST /api/.../messages    │  proxy → :8080          │                         │
  │ ───────────────────────►  │ ──────────────────►     │                         │
  │                            │                         │  POST /session           │
  │                            │                         │ ──────────────────────► │
  │                            │                         │  POST /session/.../      │
  │                            │                         │       prompt_async       │
  │                            │                         │ ──────────────────────► │
  │                            │                         │                         │
  │                            │                         │  SSE /event ◄───────────│
  │                            │                         │  (message.part.delta)    │
  │                            │                         │  ←─── text delta ────────│
  │                            │                         │                         │
  │  SSE /api/.../events       │  proxy → :8080          │  SseEmitter.push()      │
  │ ◄───────────────────────  │ ◄──────────────────     │                         │
  │  data: ASSISTANT_DELTA     │                         │                         │
```

**Key design**:
- REST for sending messages (`POST /api/agent-sessions/{id}/messages`)
- SSE for receiving streaming events (`GET /api/agent-sessions/{id}/events`)
- Java Bridge is the only thing that talks to opencode serve — the browser never connects to 4097 directly
- Session continuity: one agent_session maps to one opencode session, messages reuse the same session

## Key Files

| File | Purpose |
|---|---|
| `agentcenter-bridge/src/main/resources/application.yml` | Port, working directory, opencode config |
| `agentcenter-bridge/.../opencode/OpenCodeRuntimeAdapter.java` | REST client to opencode serve |
| `agentcenter-bridge/.../opencode/OpenCodeProcessManager.java` | Starts/manages opencode serve process |
| `agentcenter-bridge/.../opencode/OpenCodeEventSubscriber.java` | Consumes opencode SSE, translates events |
| `agentcenter-bridge/.../AgentSessionService.java` | Session + message orchestration |
| `agentcenter-web/src/views/ConversationWorkbench.vue` | Chat UI with SSE streaming |
| `agentcenter-web/src/stores/runtime.ts` | SSE connection, streaming text state |
| `agentcenter-web/vite.config.ts` | Dev server proxy config |
| `tools/opencode-bridge.mjs` | Reference Node.js implementation |
| `docs/architecture/ADR-001-*.md` | Architecture decision record |

## Shutdown

```bash
# Kill all services
pkill -f "opencode serve"
pkill -f "AgentCenterBridgeApplication"
pkill -f "spring-boot:run.*agentcenter"
# Vue dev server will die when you close the terminal, or:
lsof -ti:5173 | xargs kill
```

## Running Tests

```bash
# Backend
cd agentcenter-bridge
./mvnw test

# Frontend
cd agentcenter-web
npm run typecheck
npm run test
npm run build
```

## Known Limitations

- SSE timeout: 5 minutes per connection (configurable in `SseEmitterRegistry`)
- No WebSocket support yet (M1 is SSE-only)
- No authentication on opencode serve (local development only)
- SQLite only (no PostgreSQL for M1)
- Single-tenant, no multi-user support
- Reasoning deltas are filtered out (only text deltas shown to user)
