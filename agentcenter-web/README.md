# AgentCenter Web

Vue 3 workbench frontend for AgentCenter. Provides a visual interface for managing work items, workflows, sessions, and confirmations.

## Tech Stack

- Vue 3
- TypeScript
- Vite
- Pinia
- Vitest

## Prerequisites

- Node 20 or higher

## Install

```bash
npm install
```

## Development

Start the frontend dev server:

```bash
npm run dev
```

The frontend starts on port 5173 and proxies `/api` requests to `localhost:8080`.

## Build

```bash
npm run build
```

## Test

```bash
npm run test
```

## Typecheck

```bash
npm run typecheck
```

## Project Structure

```
agentcenter-web/
├── package.json
├── vite.config.ts
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── api/           # API client modules
│   ├── stores/        # Pinia stores
│   ├── views/         # Page components
│   │   ├── HomeOverview.vue
│   │   ├── BoardView.vue
│   │   ├── WorkflowConfig.vue
│   │   └── ConversationWorkbench.vue
│   └── components/    # Reusable components
│       ├── shell/
│       ├── workitem/
│       ├── conversation/
│       ├── confirmation/
│       └── workflow/
```

## 运行时说明

前端连接 Java Bridge（`localhost:8080`）。`runtimeType=OPENCODE` 时走真实 AI 对话：发消息通过 REST，AI 回复通过 SSE 事件流。

- SSE 连接：`src/stores/runtime.ts` 中的 `sseStream()`
- 对话工作台：`src/views/ConversationWorkbench.vue`（流式文字 + 闪烁光标）
- API 代理：`vite.config.ts` 中 `/api` → `localhost:8080`
