-- ====================================
-- AI 面试官 - V2 迁移脚本（闯关式晋级 + 熟悉度评价）
-- 执行前请备份：mysqldump -u root -p interviewer_ai > backup.sql
-- ====================================

-- ------------------------------------
-- 1. interview_session：阶段、尝试、成绩
-- ------------------------------------
ALTER TABLE interview_session
    ADD COLUMN interview_stage INT NOT NULL DEFAULT 1 COMMENT '面试阶段：1=一面, 2=二面, 3=三面',
    ADD COLUMN attempt_number INT NOT NULL DEFAULT 1 COMMENT '同一阶段内的第几次尝试',
    ADD COLUMN is_latest BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否为该阶段当前一次尝试',
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'TRUE=仅历史记录，不参与晋级判定',
    ADD COLUMN previous_session_id BIGINT NULL COMMENT '上一次尝试的 session_id',
    ADD COLUMN final_score DECIMAL(3,1) NULL COMMENT '本次面试最终得分，结束时计算写入';

-- status 取值扩展为：IN_PROGRESS / PASSED / FAILED（原 FINISHED 数据迁移）
UPDATE interview_session SET status = 'FAILED' WHERE status = 'FINISHED';

-- 晋级判定查询：WHERE interview_stage=? AND is_archived=FALSE ORDER BY final_score DESC
CREATE INDEX idx_stage_promotion ON interview_session (interview_stage, is_archived, final_score);
-- FIFO 淘汰查询：WHERE interview_stage=? ORDER BY created_at ASC LIMIT 1
CREATE INDEX idx_stage_created ON interview_session (interview_stage, created_at);

-- ------------------------------------
-- 2. chat_message：熟悉度评价
-- ------------------------------------
ALTER TABLE chat_message
    ADD COLUMN proficiency_level VARCHAR(30) NULL
        COMMENT 'NOT_FAMILIAR / FAMILIAR_BUT_SHALLOW / VERY_FAMILIAR',
    ADD COLUMN evaluation_reason TEXT NULL COMMENT '模型给出的评价理由';

-- 评分统计查询：WHERE session_id=? AND role='user' AND proficiency_level IS NOT NULL
CREATE INDEX idx_session_proficiency ON chat_message (session_id, role, proficiency_level);

SELECT 'Migration v2 completed!' AS message;
