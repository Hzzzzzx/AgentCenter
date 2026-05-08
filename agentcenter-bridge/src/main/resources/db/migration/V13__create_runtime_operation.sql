-- ============================================================
-- V13__create_runtime_operation.sql
-- Runtime operation state tracking table
-- SQLite-compatible DDL
-- ============================================================

CREATE TABLE runtime_operation (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    runtime_type TEXT NOT NULL,
    operation_type TEXT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT,
    message_id TEXT,
    correlation_id TEXT,
    agent_session_id TEXT,
    runtime_session_id TEXT,
    work_item_id TEXT,
    workflow_instance_id TEXT,
    workflow_node_instance_id TEXT,
    resource_type TEXT,
    resource_id TEXT,
    command_json TEXT,
    ack_json TEXT,
    last_event_type TEXT,
    last_event_id TEXT,
    external_status TEXT,
    external_operation_id TEXT,
    error_code TEXT,
    error_message TEXT,
    deadline_at TEXT,
    started_at TEXT,
    completed_at TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now'))
);

CREATE UNIQUE INDEX idx_runtime_operation_idempotency
    ON runtime_operation(project_id, runtime_type, operation_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_runtime_operation_status
    ON runtime_operation(status);

CREATE INDEX idx_runtime_operation_runtime_session
    ON runtime_operation(runtime_type, runtime_session_id);

CREATE INDEX idx_runtime_operation_correlation
    ON runtime_operation(correlation_id);

CREATE INDEX idx_runtime_operation_resource
    ON runtime_operation(resource_type, resource_id);
