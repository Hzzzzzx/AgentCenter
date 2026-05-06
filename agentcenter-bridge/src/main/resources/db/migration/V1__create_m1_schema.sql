-- ============================================================
-- V1__create_m1_schema.sql
-- M1 database schema: 15 tables for AgentCenter Bridge
-- SQLite-compatible DDL
-- ============================================================

-- 1. user_account
CREATE TABLE user_account (
    id TEXT PRIMARY KEY,
    external_subject TEXT,
    display_name TEXT NOT NULL,
    email TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 2. project_member
CREATE TABLE project_member (
    project_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'MEMBER',
    PRIMARY KEY (project_id, user_id)
);

-- 3. work_item
CREATE TABLE work_item (
    id TEXT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'BACKLOG',
    priority TEXT NOT NULL DEFAULT 'MEDIUM',
    project_id TEXT,
    space_id TEXT,
    iteration_id TEXT,
    owner_user_id TEXT,
    assignee_user_id TEXT,
    current_workflow_instance_id TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 4. workflow_definition
CREATE TABLE workflow_definition (
    id TEXT PRIMARY KEY,
    work_item_type TEXT NOT NULL,
    name TEXT NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    is_default INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 5. workflow_node_definition
CREATE TABLE workflow_node_definition (
    id TEXT PRIMARY KEY,
    workflow_definition_id TEXT NOT NULL,
    node_key TEXT NOT NULL,
    name TEXT NOT NULL,
    order_no INTEGER NOT NULL,
    skill_name TEXT,
    input_policy TEXT NOT NULL DEFAULT 'WORK_ITEM_ONLY',
    output_artifact_type TEXT NOT NULL DEFAULT 'MARKDOWN',
    output_name_template TEXT,
    retry_limit INTEGER NOT NULL DEFAULT 3,
    timeout_seconds INTEGER NOT NULL DEFAULT 300,
    required_confirmation INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definition(id)
);

-- 6. workflow_instance
CREATE TABLE workflow_instance (
    id TEXT PRIMARY KEY,
    work_item_id TEXT NOT NULL,
    workflow_definition_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    current_node_instance_id TEXT,
    started_at TEXT,
    completed_at TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (work_item_id) REFERENCES work_item(id),
    FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definition(id)
);

-- 7. workflow_node_instance
CREATE TABLE workflow_node_instance (
    id TEXT PRIMARY KEY,
    workflow_instance_id TEXT NOT NULL,
    node_definition_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    input_artifact_id TEXT,
    output_artifact_id TEXT,
    agent_session_id TEXT,
    runtime_session_id TEXT,
    started_at TEXT,
    completed_at TEXT,
    error_message TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id),
    FOREIGN KEY (node_definition_id) REFERENCES workflow_node_definition(id)
);

-- 8. agent_session
CREATE TABLE agent_session (
    id TEXT PRIMARY KEY,
    session_type TEXT NOT NULL DEFAULT 'GENERAL',
    title TEXT,
    work_item_id TEXT,
    workflow_instance_id TEXT,
    runtime_type TEXT NOT NULL DEFAULT 'MOCK',
    runtime_session_id TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 9. agent_message
CREATE TABLE agent_message (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT,
    content_format TEXT NOT NULL DEFAULT 'TEXT',
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    seq_no INTEGER NOT NULL,
    runtime_message_id TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (session_id) REFERENCES agent_session(id)
);

-- 10. runtime_event
CREATE TABLE runtime_event (
    id TEXT PRIMARY KEY,
    session_id TEXT,
    work_item_id TEXT,
    workflow_instance_id TEXT,
    workflow_node_instance_id TEXT,
    event_type TEXT NOT NULL,
    event_source TEXT NOT NULL,
    payload_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 11. artifact
CREATE TABLE artifact (
    id TEXT PRIMARY KEY,
    work_item_id TEXT,
    workflow_instance_id TEXT,
    workflow_node_instance_id TEXT,
    session_id TEXT,
    artifact_type TEXT NOT NULL DEFAULT 'MARKDOWN',
    title TEXT NOT NULL,
    content TEXT,
    storage_uri TEXT,
    version_no INTEGER NOT NULL DEFAULT 1,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 12. confirmation_request
CREATE TABLE confirmation_request (
    id TEXT PRIMARY KEY,
    request_type TEXT NOT NULL DEFAULT 'CONFIRM',
    status TEXT NOT NULL DEFAULT 'PENDING',
    project_id TEXT,
    space_id TEXT,
    iteration_id TEXT,
    work_item_id TEXT,
    workflow_instance_id TEXT,
    workflow_node_instance_id TEXT,
    agent_session_id TEXT,
    runtime_type TEXT,
    runtime_session_id TEXT,
    runtime_event_id TEXT,
    skill_name TEXT,
    mcp_server TEXT,
    mcp_tool TEXT,
    title TEXT NOT NULL,
    content TEXT,
    context_summary TEXT,
    options_json TEXT,
    priority TEXT NOT NULL DEFAULT 'MEDIUM',
    required_role TEXT,
    assignee_user_id TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    resolved_by TEXT,
    resolved_at TEXT,
    resolution_comment TEXT,
    resolution_payload_json TEXT
);

-- 13. confirmation_action
CREATE TABLE confirmation_action (
    id TEXT PRIMARY KEY,
    confirmation_request_id TEXT NOT NULL,
    action_type TEXT NOT NULL,
    actor_user_id TEXT,
    comment TEXT,
    payload_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (confirmation_request_id) REFERENCES confirmation_request(id)
);

-- 14. skill_definition
CREATE TABLE skill_definition (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    description TEXT,
    runtime_type TEXT NOT NULL DEFAULT 'OPENCODE',
    input_schema_json TEXT,
    output_schema_json TEXT,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 15. outbox_event
CREATE TABLE outbox_event (
    id TEXT PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    payload_json TEXT,
    status TEXT NOT NULL DEFAULT 'NEW',
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    published_at TEXT
);

-- ============================================================
-- Indexes for common queries
-- ============================================================
CREATE INDEX idx_work_item_status ON work_item(status);
CREATE INDEX idx_work_item_project ON work_item(project_id);
CREATE INDEX idx_workflow_instance_work_item ON workflow_instance(work_item_id);
CREATE INDEX idx_workflow_node_instance_workflow ON workflow_node_instance(workflow_instance_id);
CREATE INDEX idx_agent_session_work_item ON agent_session(work_item_id);
CREATE INDEX idx_agent_message_session ON agent_message(session_id);
CREATE INDEX idx_runtime_event_session ON runtime_event(session_id);
CREATE INDEX idx_confirmation_request_status ON confirmation_request(status);
CREATE INDEX idx_confirmation_request_work_item ON confirmation_request(work_item_id);
