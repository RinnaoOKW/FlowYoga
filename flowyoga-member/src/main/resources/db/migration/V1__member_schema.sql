CREATE TABLE member_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户服务的逻辑引用，不设跨库外键',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE card_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    session_count INT NOT NULL,
    valid_days INT NOT NULL,
    list_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_plan_sessions CHECK (session_count > 0),
    CONSTRAINT ck_plan_days CHECK (valid_days > 0),
    CONSTRAINT ck_plan_price CHECK (list_price >= 0),
    CONSTRAINT ck_plan_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE member_card (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    plan_name_snapshot VARCHAR(80) NOT NULL,
    list_price_snapshot DECIMAL(10,2) NOT NULL,
    valid_days_snapshot INT NOT NULL,
    total_sessions INT NOT NULL,
    remaining_sessions INT NOT NULL,
    actual_paid_amount DECIMAL(10,2) NOT NULL,
    amount_difference_reason VARCHAR(255),
    paid_at DATETIME(6) NOT NULL,
    issued_by BIGINT NOT NULL COMMENT '管理员用户编号，逻辑引用',
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    FOREIGN KEY (member_id) REFERENCES member_profile(id),
    FOREIGN KEY (plan_id) REFERENCES card_plan(id),
    INDEX idx_card_selection (member_id, status, expires_at),
    CONSTRAINT ck_card_sessions CHECK (total_sessions > 0 AND remaining_sessions BETWEEN 0 AND total_sessions),
    CONSTRAINT ck_card_amount CHECK (list_price_snapshot >= 0 AND actual_paid_amount >= 0),
    CONSTRAINT ck_card_amount_reason CHECK (actual_paid_amount = list_price_snapshot OR
        (amount_difference_reason IS NOT NULL AND CHAR_LENGTH(TRIM(amount_difference_reason)) > 0)),
    CONSTRAINT ck_card_validity CHECK (valid_days_snapshot > 0 AND expires_at >= issued_at),
    CONSTRAINT ck_card_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE card_session_ledger (
    booking_id VARCHAR(36) PRIMARY KEY COMMENT '课程预约编号，同时作为幂等键',
    member_id BIGINT NOT NULL COMMENT '请求的会员编号；释放封存允许会员尚不存在',
    requested_card_id BIGINT COMMENT '最初指定卡参数，自动选卡时为空',
    card_id BIGINT COMMENT '实际占用的卡；释放先到时可以为空',
    confirmation_deadline DATETIME(6) NOT NULL,
    state VARCHAR(16) NOT NULL,
    close_reason VARCHAR(64),
    held_at DATETIME(6),
    consumed_at DATETIME(6),
    released_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (card_id) REFERENCES member_card(id),
    INDEX idx_ledger_card_state (card_id, state),
    CONSTRAINT ck_ledger_state CHECK (state IN ('HELD', 'CONSUMED', 'RELEASED')),
    CONSTRAINT ck_ledger_card CHECK (state = 'RELEASED' OR card_id IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE member_account_task (
    request_id VARCHAR(36) PRIMARY KEY,
    member_id BIGINT,
    user_id BIGINT,
    login_name VARCHAR(50) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6),
    last_error VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (member_id) REFERENCES member_profile(id),
    INDEX idx_member_account_retry (state, next_attempt_at),
    CONSTRAINT ck_member_account_state CHECK (state IN ('PENDING', 'ACCOUNT_CREATED', 'DONE', 'DISABLE_PENDING', 'COMPENSATED')),
    CONSTRAINT ck_member_account_retries CHECK (retry_count >= 0)
) ENGINE=InnoDB;
