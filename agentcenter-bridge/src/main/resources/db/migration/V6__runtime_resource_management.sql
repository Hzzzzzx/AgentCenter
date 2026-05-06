-- ============================================================
-- V6__runtime_resource_management.sql
-- Runtime resource management tables: Skill, MCP, Audit
-- SQLite-compatible DDL
-- ============================================================

-- 1. runtime_skill
CREATE TABLE runtime_skill (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    name TEXT NOT NULL,
    display_name TEXT,
    description TEXT,
    current_version_id TEXT,
    status TEXT NOT NULL DEFAULT 'ENABLED',
    source TEXT NOT NULL DEFAULT 'UPLOAD',
    relative_path TEXT,
    checksum TEXT,
    validation_status TEXT NOT NULL DEFAULT 'VALID',
    validation_message TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(project_id, name)
);

CREATE INDEX idx_runtime_skill_project ON runtime_skill(project_id);
CREATE INDEX idx_runtime_skill_status ON runtime_skill(status);

-- 2. runtime_skill_version
CREATE TABLE runtime_skill_version (
    id TEXT PRIMARY KEY,
    skill_id TEXT NOT NULL,
    version_no TEXT NOT NULL DEFAULT '0.0.0',
    package_checksum TEXT,
    package_size INTEGER,
    file_count INTEGER,
    installed_relative_path TEXT,
    manifest_json TEXT,
    skill_md_summary TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_runtime_skill_version_skill ON runtime_skill_version(skill_id);

-- 3. project_mcp_server
CREATE TABLE project_mcp_server (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    name TEXT NOT NULL,
    server_type TEXT NOT NULL DEFAULT 'STDIO',
    status TEXT NOT NULL DEFAULT 'DISABLED',
    config_json TEXT,
    config_checksum TEXT,
    last_health_status TEXT NOT NULL DEFAULT 'UNKNOWN',
    last_health_message TEXT,
    last_checked_at TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(project_id, name)
);

CREATE INDEX idx_project_mcp_server_project ON project_mcp_server(project_id);

-- 4. project_mcp_tool_snapshot
CREATE TABLE project_mcp_tool_snapshot (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    mcp_server_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    description TEXT,
    input_schema_json TEXT,
    snapshot_version INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'AVAILABLE',
    scanned_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(mcp_server_id, tool_name, snapshot_version)
);

CREATE INDEX idx_mcp_tool_snapshot_server ON project_mcp_tool_snapshot(mcp_server_id);

-- 5. runtime_resource_audit
CREATE TABLE runtime_resource_audit (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    action TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'SUCCESS',
    summary TEXT,
    detail_json TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_runtime_resource_audit_project ON runtime_resource_audit(project_id);
CREATE INDEX idx_runtime_resource_audit_resource ON runtime_resource_audit(resource_type, resource_id);
