-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `access_token` VARCHAR(64) NOT NULL UNIQUE COMMENT 'Token（明文UUID）',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否激活（0=禁用，1=启用）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `access_expires_at` DATETIME NULL COMMENT '访问权限过期时间，NULL 表示永不过期',
    INDEX idx_token (`access_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
