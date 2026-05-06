-- ============================================================
-- V10: Orchestration strategy metadata
--
-- Workflow definitions remain the type-level strategy. Node
-- definitions are stable stages; workflow node instances may later
-- include Agent-inserted actions/recovery nodes under those stages.
-- ============================================================

ALTER TABLE workflow_node_definition ADD COLUMN stage_key TEXT;
ALTER TABLE workflow_node_definition ADD COLUMN stage_goal TEXT;
ALTER TABLE workflow_node_definition ADD COLUMN recommended_skill_names_json TEXT;
ALTER TABLE workflow_node_definition ADD COLUMN allow_dynamic_actions INTEGER NOT NULL DEFAULT 1;
ALTER TABLE workflow_node_definition ADD COLUMN confirmation_policy TEXT;

UPDATE workflow_node_definition
SET stage_key = node_key,
    stage_goal = name,
    recommended_skill_names_json = CASE
        WHEN skill_name IS NULL OR skill_name = '' THEN '[]'
        ELSE '["' || skill_name || '"]'
    END,
    confirmation_policy = CASE
        WHEN required_confirmation = 1 THEN 'REQUIRED'
        ELSE 'OPTIONAL'
    END
WHERE stage_key IS NULL;

ALTER TABLE workflow_node_instance ADD COLUMN node_kind TEXT NOT NULL DEFAULT 'STAGE';
ALTER TABLE workflow_node_instance ADD COLUMN origin TEXT NOT NULL DEFAULT 'DEFINITION';
ALTER TABLE workflow_node_instance ADD COLUMN parent_node_instance_id TEXT;
ALTER TABLE workflow_node_instance ADD COLUMN stage_key TEXT;
ALTER TABLE workflow_node_instance ADD COLUMN skill_name TEXT;
ALTER TABLE workflow_node_instance ADD COLUMN summary TEXT;
ALTER TABLE workflow_node_instance ADD COLUMN reason TEXT;
ALTER TABLE workflow_node_instance ADD COLUMN sequence_no INTEGER;

UPDATE workflow_node_instance
SET node_kind = 'STAGE',
    origin = 'DEFINITION',
    stage_key = (
        SELECT COALESCE(wnd.stage_key, wnd.node_key)
        FROM workflow_node_definition wnd
        WHERE wnd.id = workflow_node_instance.node_definition_id
    ),
    skill_name = (
        SELECT wnd.skill_name
        FROM workflow_node_definition wnd
        WHERE wnd.id = workflow_node_instance.node_definition_id
    ),
    summary = (
        SELECT wnd.name
        FROM workflow_node_definition wnd
        WHERE wnd.id = workflow_node_instance.node_definition_id
    ),
    sequence_no = (
        SELECT wnd.order_no
        FROM workflow_node_definition wnd
        WHERE wnd.id = workflow_node_instance.node_definition_id
    )
WHERE stage_key IS NULL;

CREATE INDEX idx_workflow_node_instance_parent ON workflow_node_instance(parent_node_instance_id);
CREATE INDEX idx_workflow_node_instance_stage ON workflow_node_instance(workflow_instance_id, stage_key);
