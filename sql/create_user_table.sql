-- 用户表（简化版）
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名（群友昵称）',
    `access_token` VARCHAR(64) NOT NULL UNIQUE COMMENT '访问令牌',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (`access_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入测试用户
INSERT INTO `user` (id, username, access_token) VALUES
(1, 'test_user', 'token_test123456'),
(2, 'zhang_san', 'token_zhangsan789'),
(3, 'li_si', 'token_lisi456789');
