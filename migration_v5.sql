-- V5: seven-day access for accounts created through invitation links

ALTER TABLE `user`
    ADD COLUMN access_expires_at DATETIME NULL
        COMMENT '访问权限过期时间，NULL 表示永不过期';

-- Invitation-linked users receive seven days from registration.
UPDATE `user` u
JOIN invite_code i ON i.used_by_user_id = u.id
SET access_expires_at = DATE_ADD(COALESCE(i.used_at, u.created_at), INTERVAL 7 DAY)
WHERE u.access_expires_at IS NULL;

-- Existing seeded test accounts also get seven days from their creation time.
UPDATE `user` u
LEFT JOIN invite_code i ON i.used_by_user_id = u.id
SET u.access_expires_at = DATE_ADD(u.created_at, INTERVAL 7 DAY)
WHERE i.id IS NULL AND u.access_token IN (
    'token_test123456',
    'token_admin888888',
    'token_zhangsan789',
    'token_lisi456789'
);
