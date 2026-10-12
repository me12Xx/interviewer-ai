package com.interviewer.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interviewer.ai.User;
import com.interviewer.ai.mapper.InviteCodeMapper;
import com.interviewer.ai.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterControllerTest {

    @Test
    void invitationRegistrationCreatesSevenDayAccountAndConsumesInvite() {
        UserMapper userMapper = mock(UserMapper.class);
        InviteCodeMapper inviteCodeMapper = mock(InviteCodeMapper.class);
        RegisterController controller = new RegisterController(userMapper, inviteCodeMapper);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            ((User) invocation.getArgument(0)).setId(42L);
            return 1;
        });
        when(inviteCodeMapper.markUsedIfUnused(anyString(), eq(42L))).thenReturn(1);
        when(userMapper.setAccessExpiryFromInvite(42L)).thenReturn(1);

        String inviteCode = "invite_" + UUID.randomUUID();
        var response = controller.register(inviteCode, "demo-user");

        var order = inOrder(userMapper, inviteCodeMapper);
        order.verify(userMapper).insert(any(User.class));
        order.verify(inviteCodeMapper).markUsedIfUnused(inviteCode, 42L);
        order.verify(userMapper).setAccessExpiryFromInvite(42L);
        assertEquals("demo-user", response.get("username"));
    }
}
