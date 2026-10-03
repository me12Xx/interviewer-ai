-- 邀请码表
CREATE TABLE IF NOT EXISTS `invite_code` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `code` VARCHAR(32) NOT NULL UNIQUE COMMENT '邀请码（invite_abc123）',
    `is_used` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已使用（0=未使用，1=已使用）',
    `used_by_user_id` BIGINT NULL COMMENT '被哪个用户使用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `used_at` DATETIME NULL COMMENT '使用时间',
    INDEX idx_code (`code`),
    INDEX idx_is_used (`is_used`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码表';
