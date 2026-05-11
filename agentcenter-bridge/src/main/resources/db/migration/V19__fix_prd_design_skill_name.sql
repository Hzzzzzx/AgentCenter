-- Correct the PRD skill name typo in existing workflow definitions.
UPDATE workflow_node_definition
SET skill_name = 'prd-design'
WHERE node_key = 'requirement_refine'
  AND skill_name <> 'prd-design';

UPDATE workflow_node_definition
SET recommended_skill_names_json = '["prd-design"]'
WHERE node_key = 'requirement_refine'
  AND recommended_skill_names_json <> '["prd-design"]';
