-- ============================================================
-- V18: Agent-first workflow baseline
--
-- The current baseline is the Skill-driven PRD -> HLD -> LLD
-- workflow. The earlier FE six-node rule-oriented definition is
-- no longer part of the product model, so remove it when no
-- existing workflow instance still references it.
-- ============================================================

DELETE FROM workflow_node_definition
WHERE workflow_definition_id = '01FEDEFAULTWFDEF00000000000001'
  AND NOT EXISTS (
      SELECT 1
      FROM workflow_instance
      WHERE workflow_definition_id = '01FEDEFAULTWFDEF00000000000001'
  );

DELETE FROM workflow_definition
WHERE id = '01FEDEFAULTWFDEF00000000000001'
  AND NOT EXISTS (
      SELECT 1
      FROM workflow_instance
      WHERE workflow_definition_id = '01FEDEFAULTWFDEF00000000000001'
  );

UPDATE workflow_definition
SET version_no = 1,
    status = 'ENABLED',
    is_default = 1
WHERE id IN (
    '01FEDEFAULTWFDEF00000000000002',
    '01USDEFAULTWFDEF00000000000002',
    '01TASKDEFAULTWFDEF000000000002',
    '01WORKDEFAULTWFDEF000000000002',
    '01BUGDEFAULTWFDEF0000000000002',
    '01VULNDEFAULTWFDEF000000000002'
);

UPDATE workflow_node_definition
SET required_confirmation = 0,
    allow_dynamic_actions = 1,
    confirmation_policy = 'EVENT_DRIVEN'
WHERE workflow_definition_id IN (
    '01FEDEFAULTWFDEF00000000000002',
    '01USDEFAULTWFDEF00000000000002',
    '01TASKDEFAULTWFDEF000000000002',
    '01WORKDEFAULTWFDEF000000000002',
    '01BUGDEFAULTWFDEF0000000000002',
    '01VULNDEFAULTWFDEF000000000002'
);
