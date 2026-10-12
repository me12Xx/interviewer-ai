package com.interviewer.ai.controller;

import com.interviewer.ai.InviteCode;
import com.interviewer.ai.mapper.InviteCodeMapper;
import com.interviewer.ai.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminControllerTest {

    @Test
    void createsPermanentInviteWhenAccessDaysIsZero() {
        UserMapper userMapper = mock(UserMapper.class);
        InviteCodeMapper inviteCodeMapper = mock(InviteCodeMapper.class);
        AdminController controller = new AdminController(userMapper, inviteCodeMapper);
        ReflectionTestUtils.setField(controller, "adminToken", "admin-secret");
        ReflectionTestUtils.setField(controller, "baseUrl", "https://example.test");

        Map<String, Object> response = controller.generateInviteCodes("admin-secret", 1, 0);

        var captor = org.mockito.ArgumentCaptor.forClass(InviteCode.class);
        verify(inviteCodeMapper).insert(captor.capture());
        assertNull(captor.getValue().getAccessDays());
        assertEquals(0, response.get("accessDays"));
    }

    @Test
    void rejectsInvalidAccessDays() {
        AdminController controller = new AdminController(mock(UserMapper.class), mock(InviteCodeMapper.class));
        ReflectionTestUtils.setField(controller, "adminToken", "admin-secret");

        assertThrows(RuntimeException.class,
                () -> controller.generateInviteCodes("admin-secret", 1, -1));
    }
}
