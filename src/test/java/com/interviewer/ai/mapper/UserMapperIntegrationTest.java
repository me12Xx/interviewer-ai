package com.interviewer.ai.mapper;

import com.interviewer.ai.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "typesafe.enabled=false")
@Tag("mysql")
class UserMapperIntegrationTest {

        @Autowired
        private UserMapper userMapper;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void selectsOnlyActiveAndUnexpiredUsersFromRealMysql() {
            String suffix = UUID.randomUUID().toString();
            long inviteId = Math.abs(UUID.randomUUID().getMostSignificantBits());
            List<User> users = new ArrayList<>();
            users.add(createUser("future-" + suffix));
            users.add(createUser("expired-" + suffix));
            users.add(createUser("permanent-" + suffix));

            try {
                users.forEach(userMapper::insert);
                jdbcTemplate.update("INSERT INTO invite_code "
                                + "(id, code, is_used, used_by_user_id, access_days) VALUES (?, ?, 1, ?, ?)",
                        inviteId,
                        "test_" + suffix.replace("-", "").substring(0, 27), users.get(0).getId(), 7);
                assertEquals(1, userMapper.setAccessExpiryFromInvite(users.get(0).getId()));
                jdbcTemplate.update("UPDATE `user` SET access_expires_at = DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE id = ?",
                        users.get(1).getId());

                User validUser = userMapper.selectByToken(users.get(0).getAccessToken());
                assertNotNull(validUser);
                assertNotNull(validUser.getAccessExpiresAt());
                assertNull(userMapper.selectByToken(users.get(1).getAccessToken()));
                assertNotNull(userMapper.selectByToken(users.get(2).getAccessToken()));
                assertTrue(users.get(0).getIsActive());
            } finally {
                jdbcTemplate.update("DELETE FROM invite_code WHERE id = ?", inviteId);
                users.stream().filter(user -> user.getId() != null)
                        .forEach(user -> userMapper.deleteById(user.getId()));
            }
        }

        private User createUser(String username) {
            User user = new User();
            user.setUsername(username);
            user.setAccessToken("token_" + UUID.randomUUID().toString().replace("-", ""));
            user.setIsActive(true);
            return user;
        }
}
