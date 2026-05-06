-- Keep the workflow moving by default. Only the solution design node is a
-- deliberate user-confirmation checkpoint in the prototype FE workflow.
UPDATE workflow_node_definition
SET required_confirmation = 0
WHERE node_key IN (
  'requirement_refine',
  'implementation_plan',
  'development_execute',
  'verification_review',
  'finalize_archive'
);

UPDATE workflow_node_definition
SET required_confirmation = 1
WHERE node_key = 'solution_design';
