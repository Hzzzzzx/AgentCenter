ALTER TABLE workflow_instance
    ADD COLUMN execution_mode TEXT NOT NULL DEFAULT 'MANUAL_CONFIRM';
