package com.interviewer.ai.mapper;

import com.interviewer.ai.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

    @SpringBootTest(properties = "typesafe.enabled=false")
    @Tag("mysql")
    class UserMapperIntegrationTest {

        @Autowired
        private UserMapper userMapper;

        @Test
        void selectsActiveUserByTokenFromRealMysql() {
            User user = userMapper.selectByToken("token_test123456");

            assertNotNull(user, "请先执行 sql/add_user_table.sql");
            assertEquals(1L, user.getId());
            assertEquals("test_user", user.getUsername());
            assertEquals("token_test123456", user.getAccessToken());
            assertTrue(user.getIsActive());
            assertNotNull(user.getCreatedAt());
        }
    }
