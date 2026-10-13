CREATE TABLE account_change (
    request_id VARCHAR(36) PRIMARY KEY,
    role_code VARCHAR(16) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    user_id BIGINT,
    payload_hash VARCHAR(64),
    payload_json VARCHAR(4000),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_change_role CHECK (role_code IN ('MEMBER','COACH')),
    CONSTRAINT ck_change_operation CHECK (operation IN ('CREATE','UPDATE')),
    CONSTRAINT ck_change_state CHECK (state IN ('PREPARED','APPLIED','ABORTED'))
);
CREATE INDEX idx_account_change_user ON account_change(user_id);
