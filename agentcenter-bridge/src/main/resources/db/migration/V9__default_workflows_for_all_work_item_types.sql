-- ============================================================
-- V9: Make every work item type startable with the same M1
-- PRD -> HLD -> LLD OpenCode skill chain.
-- Start and end are UI state anchors; only these three rows are
-- executable skill nodes.
-- ============================================================

INSERT INTO workflow_definition (id, work_item_type, name, version_no, status, is_default) VALUES
    ('01USDEFAULTWFDEF00000000000002', 'US', 'US 标准工作流 (PRD→HLD→LLD)', 1, 'ENABLED', 1),
    ('01TASKDEFAULTWFDEF000000000002', 'TASK', 'Task 标准工作流 (PRD→HLD→LLD)', 1, 'ENABLED', 1),
    ('01WORKDEFAULTWFDEF000000000002', 'WORK', 'Work 标准工作流 (PRD→HLD→LLD)', 1, 'ENABLED', 1),
    ('01BUGDEFAULTWFDEF0000000000002', 'BUG', '缺陷标准工作流 (PRD→HLD→LLD)', 1, 'ENABLED', 1),
    ('01VULNDEFAULTWFDEF000000000002', 'VULN', '漏洞标准工作流 (PRD→HLD→LLD)', 1, 'ENABLED', 1);

INSERT INTO workflow_node_definition (id, workflow_definition_id, node_key, name, order_no, skill_name,
    input_policy, output_artifact_type, output_name_template, required_confirmation, timeout_seconds, retry_limit) VALUES
    ('01USNODDEF000000000000000011', '01USDEFAULTWFDEF00000000000002',
        'requirement_refine', '需求整理 (PRD)', 1, 'prd-desingn',
        'WORK_ITEM_ONLY', 'MARKDOWN', '01-requirement.md', 0, 300, 3),
    ('01USNODDEF000000000000000012', '01USDEFAULTWFDEF00000000000002',
        'solution_design', '方案设计 (HLD)', 2, 'hld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '02-solution-design.md', 1, 300, 3),
    ('01USNODDEF000000000000000013', '01USDEFAULTWFDEF00000000000002',
        'implementation_plan', '详细设计 (LLD)', 3, 'lld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '03-implementation-plan.md', 0, 300, 3),

    ('01TASKNODDEF00000000000000011', '01TASKDEFAULTWFDEF000000000002',
        'requirement_refine', '需求整理 (PRD)', 1, 'prd-desingn',
        'WORK_ITEM_ONLY', 'MARKDOWN', '01-requirement.md', 0, 300, 3),
    ('01TASKNODDEF00000000000000012', '01TASKDEFAULTWFDEF000000000002',
        'solution_design', '方案设计 (HLD)', 2, 'hld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '02-solution-design.md', 1, 300, 3),
    ('01TASKNODDEF00000000000000013', '01TASKDEFAULTWFDEF000000000002',
        'implementation_plan', '详细设计 (LLD)', 3, 'lld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '03-implementation-plan.md', 0, 300, 3),

    ('01WORKNODDEF00000000000000011', '01WORKDEFAULTWFDEF000000000002',
        'requirement_refine', '需求整理 (PRD)', 1, 'prd-desingn',
        'WORK_ITEM_ONLY', 'MARKDOWN', '01-requirement.md', 0, 300, 3),
    ('01WORKNODDEF00000000000000012', '01WORKDEFAULTWFDEF000000000002',
        'solution_design', '方案设计 (HLD)', 2, 'hld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '02-solution-design.md', 1, 300, 3),
    ('01WORKNODDEF00000000000000013', '01WORKDEFAULTWFDEF000000000002',
        'implementation_plan', '详细设计 (LLD)', 3, 'lld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '03-implementation-plan.md', 0, 300, 3),

    ('01BUGNODDEF000000000000000011', '01BUGDEFAULTWFDEF0000000000002',
        'requirement_refine', '需求整理 (PRD)', 1, 'prd-desingn',
        'WORK_ITEM_ONLY', 'MARKDOWN', '01-requirement.md', 0, 300, 3),
    ('01BUGNODDEF000000000000000012', '01BUGDEFAULTWFDEF0000000000002',
        'solution_design', '方案设计 (HLD)', 2, 'hld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '02-solution-design.md', 1, 300, 3),
    ('01BUGNODDEF000000000000000013', '01BUGDEFAULTWFDEF0000000000002',
        'implementation_plan', '详细设计 (LLD)', 3, 'lld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '03-implementation-plan.md', 0, 300, 3),

    ('01VULNNODDEF00000000000000011', '01VULNDEFAULTWFDEF000000000002',
        'requirement_refine', '需求整理 (PRD)', 1, 'prd-desingn',
        'WORK_ITEM_ONLY', 'MARKDOWN', '01-requirement.md', 0, 300, 3),
    ('01VULNNODDEF00000000000000012', '01VULNDEFAULTWFDEF000000000002',
        'solution_design', '方案设计 (HLD)', 2, 'hld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '02-solution-design.md', 1, 300, 3),
    ('01VULNNODDEF00000000000000013', '01VULNDEFAULTWFDEF000000000002',
        'implementation_plan', '详细设计 (LLD)', 3, 'lld-design',
        'PREVIOUS_ARTIFACT', 'MARKDOWN', '03-implementation-plan.md', 0, 300, 3);
