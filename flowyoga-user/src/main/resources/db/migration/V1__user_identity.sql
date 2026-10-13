CREATE TABLE auth_role (
    role_code VARCHAR(16) PRIMARY KEY,
    role_name VARCHAR(32) NOT NULL
);
CREATE TABLE auth_permission (
    permission_code VARCHAR(64) PRIMARY KEY,
    permission_name VARCHAR(64) NOT NULL
);
CREATE TABLE auth_role_permission (
    role_code VARCHAR(16) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, permission_code),
    FOREIGN KEY (role_code) REFERENCES auth_role(role_code),
    FOREIGN KEY (permission_code) REFERENCES auth_permission(permission_code)
);
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_name VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    role_code VARCHAR(16) NOT NULL,
    creation_request_id VARCHAR(36) UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (role_code) REFERENCES auth_role(role_code),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE TABLE auth_session (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_session_time CHECK (expires_at > created_at)
);
CREATE INDEX idx_session_user_expiry ON auth_session(user_id, expires_at);
CREATE INDEX idx_session_expiry ON auth_session(expires_at);

INSERT INTO auth_role VALUES ('ADMIN', '管理员'), ('COACH', '教练'), ('MEMBER', '会员');
INSERT INTO auth_permission VALUES
 ('member:manage','会员管理'), ('coach:manage','教练管理'), ('course:manage','课程排课'),
 ('card:issue','会员开卡'), ('report:read','运营统计'), ('schedule:own','我的课表'),
 ('attendance:write','到场登记'), ('schedule:read','查看课表'), ('booking:own','我的预约'), ('card:own','我的会员卡');
INSERT INTO auth_role_permission VALUES
 ('ADMIN','member:manage'), ('ADMIN','coach:manage'), ('ADMIN','course:manage'), ('ADMIN','card:issue'), ('ADMIN','report:read'),
 ('COACH','schedule:own'), ('COACH','attendance:write'),
 ('MEMBER','schedule:read'), ('MEMBER','booking:own'), ('MEMBER','card:own');
