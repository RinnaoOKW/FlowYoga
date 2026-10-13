CREATE TABLE coach_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户服务的逻辑引用',
    specialty VARCHAR(255),
    introduction VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_coach_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE yoga_course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    default_duration_minutes INT NOT NULL,
    introduction VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_course_duration CHECK (default_duration_minutes BETWEEN 1 AND 240),
    CONSTRAINT ck_course_difficulty CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_course_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE studio_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    capacity INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_room_capacity CHECK (capacity > 0),
    CONSTRAINT ck_room_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE class_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    coach_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    seat_limit INT NOT NULL,
    occupied_seats INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    version INT NOT NULL DEFAULT 0,
    cancelled_at DATETIME(6),
    cancellation_reason VARCHAR(255),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (course_id) REFERENCES yoga_course(id),
    FOREIGN KEY (coach_id) REFERENCES coach_profile(id),
    FOREIGN KEY (room_id) REFERENCES studio_room(id),
    INDEX idx_session_coach_time (coach_id, starts_at, ends_at),
    INDEX idx_session_room_time (room_id, starts_at, ends_at),
    INDEX idx_session_status_time (status, starts_at),
    CONSTRAINT ck_session_interval CHECK (ends_at > starts_at AND TIMESTAMPDIFF(SECOND, starts_at, ends_at) <= 14400),
    CONSTRAINT ck_session_seats CHECK (seat_limit > 0 AND occupied_seats BETWEEN 0 AND seat_limit),
    CONSTRAINT ck_class_status CHECK (status IN ('OPEN', 'CANCELLED', 'FINISHED'))
) ENGINE=InnoDB;

CREATE TABLE course_booking (
    id VARCHAR(36) PRIMARY KEY,
    class_session_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL COMMENT '会员服务档案编号，逻辑引用',
    requested_card_id BIGINT,
    card_id BIGINT COMMENT '确认后的实际会员卡编号，逻辑引用',
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    sync_state VARCHAR(24) NOT NULL DEFAULT 'PENDING_HOLD',
    confirmation_deadline DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6),
    signed_in_at DATETIME(6),
    signed_in_by BIGINT COMMENT '签到操作教练的用户编号',
    closed_at DATETIME(6),
    close_reason VARCHAR(64),
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active_member_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('CANCELLED', 'FAILED') THEN NULL ELSE member_id END) STORED,
    FOREIGN KEY (class_session_id) REFERENCES class_session(id),
    UNIQUE KEY uk_booking_active (class_session_id, active_member_id),
    INDEX idx_booking_member_time (member_id, created_at),
    INDEX idx_booking_pending_deadline (status, confirmation_deadline),
    CONSTRAINT ck_booking_status CHECK (status IN ('PROCESSING', 'BOOKED', 'CHECKED_IN', 'COMPLETED', 'ABSENT', 'CANCELLED', 'FAILED')),
    CONSTRAINT ck_booking_sync CHECK (sync_state IN ('PENDING_HOLD', 'HELD', 'PENDING_RELEASE', 'RELEASED', 'PENDING_CONSUME', 'CONSUMED')),
    CONSTRAINT ck_booking_deadline CHECK (confirmation_deadline > created_at),
    CONSTRAINT ck_booking_confirmation CHECK (status IN ('PROCESSING', 'CANCELLED', 'FAILED') OR confirmed_at IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE session_sync_task (
    booking_id VARCHAR(36) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    requires_review BOOLEAN NOT NULL DEFAULT FALSE,
    next_attempt_at DATETIME(6) NOT NULL,
    locked_until DATETIME(6),
    last_error VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (booking_id, operation),
    FOREIGN KEY (booking_id) REFERENCES course_booking(id),
    INDEX idx_sync_task_retry (state, next_attempt_at),
    CONSTRAINT ck_sync_operation CHECK (operation IN ('HOLD', 'RELEASE', 'CONSUME')),
    CONSTRAINT ck_sync_task_state CHECK (state IN ('PENDING', 'RUNNING', 'DONE')),
    CONSTRAINT ck_sync_task_retries CHECK (retry_count >= 0)
) ENGINE=InnoDB;

CREATE TABLE coach_account_task (
    request_id VARCHAR(36) PRIMARY KEY,
    coach_id BIGINT,
    user_id BIGINT,
    login_name VARCHAR(50) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6),
    last_error VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (coach_id) REFERENCES coach_profile(id),
    INDEX idx_coach_account_retry (state, next_attempt_at),
    CONSTRAINT ck_coach_account_state CHECK (state IN ('PENDING', 'ACCOUNT_CREATED', 'DONE', 'DISABLE_PENDING', 'COMPENSATED')),
    CONSTRAINT ck_coach_account_retries CHECK (retry_count >= 0)
) ENGINE=InnoDB;
