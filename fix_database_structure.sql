-- ====================================
-- V2 数据库表结构修复脚本
-- 执行前请确保已备份数据库
-- ====================================

USE interviewer_ai;

-- 1. 检查并添加 user_id 列到 interview_session 表
SELECT COLUMN_NAME 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'interviewer_ai' 
  AND TABLE_NAME = 'interview_session' 
  AND COLUMN_NAME = 'user_id';

-- 如果上面查询返回空，执行下面的语句
ALTER TABLE interview_session 
ADD COLUMN user_id BIGINT NULL COMMENT '用户ID（V2 新增，暂时硬编码为1）'
AFTER id;

-- 2. 检查并添加 tool_used 列到 question 表
SELECT COLUMN_NAME 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'interviewer_ai' 
  AND TABLE_NAME = 'question' 
  AND COLUMN_NAME = 'tool_used';

-- 如果上面查询返回空，执行下面的语句
ALTER TABLE question 
ADD COLUMN tool_used VARCHAR(50) NULL COMMENT '使用的工具：ask_followup/change_topic/finish_interview'
AFTER round_num;

-- 3. 验证修改结果
DESC interview_session;
DESC question;

-- 4. 查看示例数据
SELECT id, user_id, target_position, status FROM interview_session LIMIT 3;
SELECT id, session_id, round_num, tool_used FROM question LIMIT 3;

-- ====================================
-- 执行完成后，重启应用即可测试
-- ====================================
