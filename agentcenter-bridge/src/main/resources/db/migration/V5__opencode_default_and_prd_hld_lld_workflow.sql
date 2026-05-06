-- ============================================================
-- V5: Switch default runtime to OPENCODE and streamline FE workflow
-- to PRD → HLD → LLD (3 nodes with real OpenCode skills)
-- ============================================================

-- 1. Change agent_session.runtime_type default from MOCK to OPENCODE
--    SQLite does not support ALTER TABLE ... ALTER COLUMN,
--    so we recreate the table with the corrected default.
--    Existing data is preserved via the rename-swap pattern.

CREATE TABLE agent_session_new (
    id TEXT PRIMARY KEY,
    session_type TEXT NOT NULL DEFAULT 'GENERAL',
    title TEXT,
    work_item_id TEXT,
    workflow_instance_id TEXT,
    runtime_type TEXT NOT NULL DEFAULT 'OPENCODE',
    runtime_session_id TEXT,
    working_directory TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO agent_session_new (id, session_type, title, work_item_id, workflow_instance_id,
    runtime_type, runtime_session_id, status, created_by, created_at, updated_at)
SELECT id, session_type, title, work_item_id, workflow_instance_id,
    COALESCE(runtime_type, 'OPENCODE'), runtime_session_id, status, created_by, created_at, updated_at
FROM agent_session;

DROP TABLE agent_session;
ALTER TABLE agent_session_new RENAME TO agent_session;

CREATE INDEX idx_agent_session_work_item ON agent_session(work_item_id);

-- 2. Update existing MOCK sessions to OPENCODE
UPDATE agent_session SET runtime_type = 'OPENCODE' WHERE runtime_type = 'MOCK';

-- 3. Replace the 6-node FE workflow with a clean PRD → HLD → LLD 3-node version
--    Disable the old workflow definition
UPDATE workflow_definition SET status = 'DISABLED', is_default = 0
WHERE id = '01FEDEFAULTWFDEF00000000000001';

--    Insert a new default FE workflow definition (v2)
INSERT INTO workflow_definition (id, work_item_type, name, version_no, status, is_default) VALUES
    ('01FEDEFAULTWFDEF00000000000002', 'FE', 'FE 标准工作流 (PRD→HLD→LLD)', 2, 'ENABLED', 1);

--    Insert 3 nodes: PRD → HLD → LLD
INSERT INTO workflow_node_definition (id, workflow_definition_id, node_key, name, order_no, skill_name,
    input_policy, output_artifact_type, output_name_template, required_confirmation, timeout_seconds, retry_limit) VALUES
    ('01FENODDEF000000000000000011', '01FEDEFAULTWFDEF00000000000002',
        'requirement_refine', '需求整理 (PRD)', 1, 'prd-desingn',
        'WORK_ITEM_ONLY', 'MARKDOWN', '01-requirement.md', 0, 300, 3),

    ('01FENODDEF000000000000000012', '01FEDEFAULTWFDEF00000000000002',
        'solution_design', '方案设计 (HLD)', 2, 'hld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '02-solution-design.md', 1, 300, 3),

    ('01FENODDEF000000000000000013', '01FEDEFAULTWFDEF00000000000002',
        'implementation_plan', '详细设计 (LLD)', 3, 'lld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '03-implementation-plan.md', 0, 300, 3);
