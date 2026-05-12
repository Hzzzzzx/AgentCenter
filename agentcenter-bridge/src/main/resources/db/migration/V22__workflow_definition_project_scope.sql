ALTER TABLE workflow_definition
    ADD COLUMN project_id TEXT NOT NULL DEFAULT '01DEFAULTPROJECT0000000000001';

CREATE INDEX idx_workflow_definition_project_type
    ON workflow_definition(project_id, work_item_type, status, is_default);
