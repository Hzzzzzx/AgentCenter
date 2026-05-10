-- Phase 1: Node State Protocol - Add protocol state columns to workflow_node_instance
ALTER TABLE workflow_node_instance ADD COLUMN agent_state TEXT DEFAULT NULL;
ALTER TABLE workflow_node_instance ADD COLUMN agent_state_reason TEXT DEFAULT NULL;
ALTER TABLE workflow_node_instance ADD COLUMN agent_state_artifact_title TEXT DEFAULT NULL;
ALTER TABLE workflow_node_instance ADD COLUMN agent_state_payload_json TEXT DEFAULT NULL;
ALTER TABLE workflow_node_instance ADD COLUMN agent_state_updated_at TEXT DEFAULT NULL;

-- Phase 1: Add interaction protocol columns to confirmation_request (reusing table for interactions)
ALTER TABLE confirmation_request ADD COLUMN interaction_id TEXT DEFAULT NULL;
ALTER TABLE confirmation_request ADD COLUMN interaction_type TEXT DEFAULT NULL;
ALTER TABLE confirmation_request ADD COLUMN interaction_schema_json TEXT DEFAULT NULL;
ALTER TABLE confirmation_request ADD COLUMN interaction_context_json TEXT DEFAULT NULL;
ALTER TABLE confirmation_request ADD COLUMN interaction_required INTEGER DEFAULT 1;
ALTER TABLE confirmation_request ADD COLUMN interaction_order_no INTEGER DEFAULT NULL;
