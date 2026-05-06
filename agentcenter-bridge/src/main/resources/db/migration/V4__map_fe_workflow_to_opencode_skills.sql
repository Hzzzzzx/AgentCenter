-- Align AgentCenter FE workflow node names with the actual OpenCode skills
-- installed in .opencode/skills for the prototype bridge.
UPDATE workflow_node_definition
SET skill_name = 'prd-desingn'
WHERE node_key = 'requirement_refine';

UPDATE workflow_node_definition
SET skill_name = 'hld-design'
WHERE node_key = 'solution_design';

UPDATE workflow_node_definition
SET skill_name = 'lld-design'
WHERE node_key = 'implementation_plan';
