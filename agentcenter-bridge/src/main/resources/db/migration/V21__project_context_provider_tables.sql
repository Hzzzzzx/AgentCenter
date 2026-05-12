CREATE TABLE project_context (
    id TEXT PRIMARY KEY,
    provider_id TEXT NOT NULL,
    external_project_id TEXT NOT NULL,
    project_name TEXT NOT NULL,
    external_cloude_req_project_id TEXT,
    cloude_req_project_name TEXT,
    active INTEGER NOT NULL DEFAULT 0,
    extra_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(provider_id, external_project_id)
);

CREATE TABLE project_space (
    id TEXT PRIMARY KEY,
    provider_id TEXT NOT NULL,
    project_context_id TEXT NOT NULL,
    external_space_id TEXT NOT NULL,
    space_name TEXT NOT NULL,
    extra_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(provider_id, project_context_id, external_space_id),
    FOREIGN KEY (project_context_id) REFERENCES project_context(id)
);

CREATE TABLE project_iteration (
    id TEXT PRIMARY KEY,
    provider_id TEXT NOT NULL,
    project_context_id TEXT NOT NULL,
    project_space_id TEXT NOT NULL,
    external_iteration_id TEXT NOT NULL,
    iteration_name TEXT NOT NULL,
    status TEXT,
    start_at TEXT,
    end_at TEXT,
    extra_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(provider_id, project_space_id, external_iteration_id),
    FOREIGN KEY (project_context_id) REFERENCES project_context(id),
    FOREIGN KEY (project_space_id) REFERENCES project_space(id)
);

CREATE TABLE project_provider_setting (
    id TEXT PRIMARY KEY,
    active_provider_id TEXT NOT NULL,
    active_project_context_id TEXT,
    active_project_space_id TEXT,
    active_project_iteration_id TEXT,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO project_provider_setting (id, active_provider_id)
VALUES ('global', 'fixture-alpha');

CREATE TABLE project_data_sync_history (
    id TEXT PRIMARY KEY,
    provider_id TEXT NOT NULL,
    status TEXT NOT NULL,
    context_count INTEGER NOT NULL DEFAULT 0,
    work_item_count INTEGER NOT NULL DEFAULT 0,
    active_project_context_id TEXT,
    active_project_space_id TEXT,
    active_project_iteration_id TEXT,
    result_json TEXT,
    error_message TEXT,
    started_at TEXT NOT NULL,
    completed_at TEXT
);

ALTER TABLE work_item ADD COLUMN provider_id TEXT;
ALTER TABLE work_item ADD COLUMN external_work_item_id TEXT;
ALTER TABLE work_item ADD COLUMN project_context_id TEXT;
ALTER TABLE work_item ADD COLUMN project_space_id TEXT;
ALTER TABLE work_item ADD COLUMN project_iteration_id TEXT;
ALTER TABLE work_item ADD COLUMN extra_json TEXT;

CREATE INDEX idx_project_context_provider ON project_context(provider_id);
CREATE INDEX idx_project_space_context ON project_space(project_context_id);
CREATE INDEX idx_project_iteration_space ON project_iteration(project_space_id);
CREATE INDEX idx_project_provider_setting_provider ON project_provider_setting(active_provider_id);
CREATE INDEX idx_project_data_sync_history_provider
    ON project_data_sync_history(provider_id, started_at DESC);
CREATE INDEX idx_work_item_provider ON work_item(provider_id);
CREATE INDEX idx_work_item_context_scope ON work_item(project_context_id, project_space_id, project_iteration_id);
CREATE UNIQUE INDEX idx_work_item_provider_external
    ON work_item(provider_id, external_work_item_id)
    WHERE provider_id IS NOT NULL AND external_work_item_id IS NOT NULL;
