-- Default user
INSERT INTO user_account (id, display_name, email) VALUES
    ('01DEFAULTUSER00000000000000001', 'Admin', 'admin@agentcenter.dev');

-- Default project membership
INSERT INTO project_member (project_id, user_id, role) VALUES
    ('01DEFAULTPROJECT0000000000001', '01DEFAULTUSER00000000000000001', 'OWNER');

-- Work items
INSERT INTO work_item (id, code, type, title, status, priority, project_id, owner_user_id, assignee_user_id) VALUES
    ('01WORKITEM0000000000000FE1234', 'FE1234', 'FE', '用户登录优化', 'BACKLOG', 'HIGH', '01DEFAULTPROJECT0000000000001', '01DEFAULTUSER00000000000000001', '01DEFAULTUSER00000000000000001'),
    ('01WORKITEM0000000000000US1203', 'US1203', 'US', '首页加载性能提升', 'TODO', 'MEDIUM', '01DEFAULTPROJECT0000000000001', '01DEFAULTUSER00000000000000001', '01DEFAULTUSER00000000000000001'),
    ('01WORKITEM000000000000BUG0602', 'BUG0602', 'BUG', '看板拖拽排序异常', 'IN_PROGRESS', 'URGENT', '01DEFAULTPROJECT0000000000001', '01DEFAULTUSER00000000000000001', '01DEFAULTUSER00000000000000001');

-- FE default workflow definition
INSERT INTO workflow_definition (id, work_item_type, name, version_no, status, is_default) VALUES
    ('01FEDEFAULTWFDEF00000000000001', 'FE', 'FE 默认工作流', 1, 'ENABLED', 1);

-- FE workflow node definitions (6 nodes)
INSERT INTO workflow_node_definition (id, workflow_definition_id, node_key, name, order_no, skill_name) VALUES
    ('01FENODDEF000000000000000001', '01FEDEFAULTWFDEF00000000000001', 'requirement_refine', '需求整理与完善', 1, 'fe.requirement.refine'),
    ('01FENODDEF000000000000000002', '01FEDEFAULTWFDEF00000000000001', 'solution_design', '方案设计', 2, 'fe.solution.design'),
    ('01FENODDEF000000000000000003', '01FEDEFAULTWFDEF00000000000001', 'implementation_plan', '实施计划', 3, 'fe.implementation.plan'),
    ('01FENODDEF000000000000000004', '01FEDEFAULTWFDEF00000000000001', 'development_execute', '开发执行', 4, 'fe.development.execute'),
    ('01FENODDEF000000000000000005', '01FEDEFAULTWFDEF00000000000001', 'verification_review', '验证与评审', 5, 'fe.verification.review'),
    ('01FENODDEF000000000000000006', '01FEDEFAULTWFDEF00000000000001', 'finalize_archive', '完成归档', 6, 'fe.finalize.archive');
