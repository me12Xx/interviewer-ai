-- ====================================
-- AI 面试官 - 数据库初始化脚本
-- ====================================
-- 使用方法：
--   1. 创建数据库：CREATE DATABASE interviewer_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   2. 使用数据库：USE interviewer_ai;
--   3. 执行本脚本
-- ====================================

-- ====================================
-- 1. 面试会话表
-- ====================================
CREATE TABLE IF NOT EXISTS interview_session (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花主键',
    user_id BIGINT NULL COMMENT '用户 ID',
    resume_text TEXT NOT NULL COMMENT '简历内容',
    target_position VARCHAR(100) NOT NULL COMMENT '目标岗位',
    position VARCHAR(50) NOT NULL DEFAULT 'java' COMMENT '岗位模板键',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS / PASSED / FAILED',
    current_round INT NOT NULL DEFAULT 1 COMMENT '当前轮次',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    finished_at DATETIME(3) NULL COMMENT '结束时间',
    interview_stage INT NOT NULL DEFAULT 1 COMMENT '面试阶段',
    attempt_number INT NOT NULL DEFAULT 1 COMMENT '当前阶段尝试次数',
    is_latest BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否当前阶段最新一次',
    is_archived BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否归档',
    previous_session_id BIGINT NULL COMMENT '前一次会话 ID',
    final_score DECIMAL(3,1) NULL COMMENT '最终分数',
    lease_owner VARCHAR(64) NULL COMMENT '当前会话执行权持有者',
    lease_expires_at DATETIME(3) NULL COMMENT '会话执行权过期时间',
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_stage_promotion (interview_stage, is_archived, final_score),
    INDEX idx_stage_created (interview_stage, created_at),
    INDEX idx_session_lease (lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试会话表';

CREATE TABLE IF NOT EXISTS interview_request (
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
    FOREIGN KEY (session_id) REFERENCES interview_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试请求幂等记录';

-- ====================================
-- 2. 对话记录表
-- ====================================
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user / assistant / tool',
    content TEXT NOT NULL COMMENT '消息内容',
    round_num INT NOT NULL COMMENT '所属轮次',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_round_num (round_num),
    FOREIGN KEY (session_id) REFERENCES interview_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话记录表';

-- ====================================
-- 3. 面试题目表
-- ====================================
CREATE TABLE IF NOT EXISTS question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    content TEXT NOT NULL COMMENT '题目内容',
    category VARCHAR(50) NULL COMMENT '题目分类（可选）',
    round_num INT NOT NULL COMMENT '所属轮次',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_category (category),
    FOREIGN KEY (session_id) REFERENCES interview_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试题目表';

-- ====================================
-- 4. 评分报告表
-- ====================================
CREATE TABLE IF NOT EXISTS assessment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    question_id BIGINT NULL COMMENT '题目 ID（可选）',
    score DECIMAL(3,1) NOT NULL COMMENT '评分（0-10）',
    feedback TEXT NOT NULL COMMENT '详细评价',
    weak_points TEXT NULL COMMENT '薄弱点',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_score (score),
    FOREIGN KEY (session_id) REFERENCES interview_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评分报告表';

-- ====================================
-- 初始化完成
-- ====================================
SELECT 'Database initialized successfully!' AS message;
