-- V6: administrator-controlled access duration for invitation-created accounts

ALTER TABLE `invite_code`
    ADD COLUMN access_days INT NULL
        COMMENT '注册账号有效天数，NULL 表示永不过期';

-- Invitations created before V6 used the original seven-day behavior.
UPDATE `invite_code`
SET access_days = 7
WHERE access_days IS NULL;
