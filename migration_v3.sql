-- V3: request idempotency and database-backed session lease

ALTER TABLE interview_session
    ADD COLUMN lease_owner VARCHAR(64) NULL COMMENT '当前会话执行权持有者',
    ADD COLUMN lease_expires_at DATETIME(3) NULL COMMENT '会话执行权过期时间';

CREATE INDEX idx_session_lease ON interview_session (lease_expires_at);

CREATE TABLE interview_request (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花主键',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    request_id VARCHAR(100) NOT NULL COMMENT '客户端幂等请求 ID',
    status VARCHAR(20) NOT NULL COMMENT 'PROCESSING / COMPLETED / FAILED',
    response_json JSON NULL COMMENT '成功响应',
    failure_message VARCHAR(500) NULL COMMENT '失败摘要',
    expires_at DATETIME(3) NULL COMMENT '失败记录可清理时间',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_session_request (session_id, request_id),
    INDEX idx_request_expiry (status, expires_at),
    CONSTRAINT fk_request_session FOREIGN KEY (session_id)
        REFERENCES interview_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试请求幂等记录';
