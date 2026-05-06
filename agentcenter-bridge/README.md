# AgentCenter Bridge

Java Spring Boot 3 backend service for AgentCenter. Manages work items, workflows, sessions, confirmations, and runtime adapters.

## Tech Stack

- Java 17
- Spring Boot 3.3.x
- MyBatis
- SQLite (development) / PostgreSQL (production)
- Flyway
- ULID

## Prerequisites

- Java 17 or higher
- Maven 3.8+ (or use the included `./mvnw` wrapper)

## Build

```bash
./mvnw clean package
```

## Test

```bash
./mvnw test
```

## Run

Start the backend service:

```bash
./mvnw spring-boot:run
```

The service starts on port 8080.

## API Endpoints

### Work Items

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/work-items | List all work items |
| POST | /api/work-items | Create a work item |
| PUT | /api/work-items/{id} | Update a work item |
| POST | /api/work-items/{id}/start-workflow | Start workflow for a work item |

### Workflow Definitions

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/workflow-definitions | List all workflow definitions |

### Workflow Instances

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/workflow-instances/{id} | Get workflow instance details |
| POST | /api/workflow-instances/{id}/continue | Continue a workflow instance |

### Workflow Node Instances

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/workflow-node-instances/{id}/retry | Retry a failed node |
| POST | /api/workflow-node-instances/{id}/skip | Skip a node |

### Agent Sessions

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/agent-sessions | List all sessions |
| POST | /api/agent-sessions | Create a session |
| GET | /api/agent-sessions/{id}/messages | Get session messages |
| POST | /api/agent-sessions/{id}/messages | Send a message |
| GET | /api/agent-sessions/{id}/events | SSE event stream |

### Confirmations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/confirmations | List confirmations |
| POST | /api/confirmations/{id}/resolve | Resolve a confirmation |
| POST | /api/confirmations/{id}/reject | Reject a confirmation |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /health | Health check |

## Database

SQLite file at `./data/agentcenter.db`, managed by Flyway migrations.

## Architecture

DDD-style layered architecture:

```
Controller       -> API endpoints, parameter validation
Application      -> Business orchestration
Domain           -> Business rules and state
Infrastructure   -> Persistence, runtime adapters
```

## M1 Scope Note

The OpenCode adapter is not implemented yet. This project uses `MockRuntimeAdapter` to simulate runtime behavior without requiring an actual OpenCode instance.

## Project Structure

```
agentcenter-bridge/
├── pom.xml
├── src/main/java/com/agentcenter/bridge/
│   ├── AgentCenterBridgeApplication.java
│   ├── api/
│   │   ├── WorkItemController.java
│   │   ├── WorkflowController.java
│   │   ├── AgentSessionController.java
│   │   ├── ConfirmationController.java
│   │   └── RuntimeEventStreamController.java
│   ├── application/
│   ├── domain/
│   │   ├── workitem/
│   │   ├── workflow/
│   │   ├── session/
│   │   ├── confirmation/
│   │   └── runtime/
│   ├── infrastructure/
│   │   ├── persistence/
│   │   └── runtime/mock/
│   └── worker/
├── src/main/resources/
│   ├── application.yml
│   ├── mapper/
│   └── db/migration/
└── src/test/
```
