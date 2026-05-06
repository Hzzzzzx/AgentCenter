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

## M1 Scope Note

This frontend connects to the Java backend at `localhost:8080`. During M1, it exercises the complete data loop using mock runtime adapters without requiring OpenCode.
