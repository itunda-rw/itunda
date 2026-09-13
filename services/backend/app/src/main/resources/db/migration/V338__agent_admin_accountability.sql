ALTER TABLE agents ADD COLUMN status_changed_by_user_id VARCHAR(64) NULL;
ALTER TABLE agents ADD COLUMN status_changed_at DATETIME(6) NULL;
ALTER TABLE agent_operators ADD COLUMN assigned_by_user_id VARCHAR(64) NULL;
ALTER TABLE agent_operators ADD COLUMN status_changed_by_user_id VARCHAR(64) NULL;
ALTER TABLE agent_operators ADD COLUMN status_changed_at DATETIME(6) NULL;
