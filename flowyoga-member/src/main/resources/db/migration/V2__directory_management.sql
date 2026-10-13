ALTER TABLE member_profile ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE member_account_task ADD COLUMN operation VARCHAR(16) NOT NULL DEFAULT 'CREATE';
ALTER TABLE member_account_task ADD COLUMN payload_json VARCHAR(4000) NOT NULL DEFAULT '{}';
ALTER TABLE member_account_task ADD COLUMN payload_hash VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE member_account_task ADD COLUMN expected_version INT NOT NULL DEFAULT 0;
ALTER TABLE member_account_task ADD COLUMN locked_until DATETIME(6);
CREATE INDEX idx_directory_pending_user ON member_account_task(user_id, state);