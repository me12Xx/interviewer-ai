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
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    resume_text TEXT NOT NULL COMMENT '简历内容',
    target_position VARCHAR(100) NOT NULL COMMENT '目标岗位',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS / FINISHED',
    current_round INT NOT NULL DEFAULT 1 COMMENT '当前轮次',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finished_at DATETIME NULL COMMENT '结束时间',
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试会话表';

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
