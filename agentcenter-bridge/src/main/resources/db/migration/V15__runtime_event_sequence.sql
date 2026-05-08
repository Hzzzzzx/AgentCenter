ALTER TABLE runtime_event ADD COLUMN seq_no INTEGER;

UPDATE runtime_event
SET seq_no = rowid
WHERE seq_no IS NULL;

CREATE INDEX idx_runtime_event_session_seq ON runtime_event(session_id, seq_no);
