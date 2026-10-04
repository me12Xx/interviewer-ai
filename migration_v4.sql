-- V4: structured agent model/tool call trace

CREATE TABLE agent_turn_log (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花主键',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    request_id VARCHAR(100) NULL COMMENT '客户端幂等请求 ID',
    trace_id VARCHAR(64) NOT NULL COMMENT '本次回答链路追踪 ID',
    round_num INT NULL COMMENT '面试轮次',
    turn_seq INT NOT NULL COMMENT '本次链路内模型调用序号',
    context_type VARCHAR(30) NOT NULL COMMENT 'FIRST_QUESTION / ANSWER',
    prompt_version VARCHAR(50) NULL COMMENT 'Prompt 版本',
    model VARCHAR(100) NULL COMMENT '模型名称',
    tool_name VARCHAR(50) NULL COMMENT '工具名称',
    decision VARCHAR(30) NOT NULL COMMENT 'ACCEPTED / REJECTED / DEGRADED / EXECUTED',
    finish_reason VARCHAR(50) NULL COMMENT '模型结束原因',
    proficiency_level VARCHAR(50) NULL COMMENT '熟悉度',
    degrade_reason VARCHAR(50) NULL COMMENT '降级原因',
    token_in INT NULL COMMENT '输入 token',
    token_out INT NULL COMMENT '输出 token',
    latency_ms BIGINT NULL COMMENT '模型调用耗时',
    error_code VARCHAR(50) NULL COMMENT '标准错误码',
    error_message VARCHAR(500) NULL COMMENT '截断后的错误摘要',
    created_at DATETIME(3) NOT NULL,
    INDEX idx_turn_session_round (session_id, round_num),
    INDEX idx_turn_trace (trace_id, turn_seq),
    INDEX idx_turn_created (created_at),
    CONSTRAINT fk_turn_session FOREIGN KEY (session_id)
        REFERENCES interview_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 调用轨迹';
