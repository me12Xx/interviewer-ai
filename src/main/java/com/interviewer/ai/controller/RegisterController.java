package com.interviewer.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interviewer.ai.User;
import com.interviewer.ai.exception.ApiException;
import com.interviewer.ai.exception.ErrorCode;
import com.interviewer.ai.mapper.InviteCodeMapper;
import com.interviewer.ai.mapper.UserMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 注册控制器
 */
@RestController
@RequestMapping("/api")
public class RegisterController {
    
    private final UserMapper userMapper;
    private final InviteCodeMapper inviteCodeMapper;
    
    public RegisterController(UserMapper userMapper, InviteCodeMapper inviteCodeMapper) {
        this.userMapper = userMapper;
        this.inviteCodeMapper = inviteCodeMapper;
    }
    
    /**
     * 使用邀请码注册
     *
     * 请求示例：
     * POST /api/register
     * Body: inviteCode=invite_abc123&username=张三
     */
    @PostMapping("/register")
    @Transactional
    public Map<String, Object> register(
            @RequestParam String inviteCode,
            @RequestParam String username) {
        
        // 1. 验证昵称
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT, "昵称不能为空");
        }
        
        username = username.trim();
        
        if (username.length() > 20) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT, "昵称长度不能超过20个字符");
        }
        
        // 2. 检查昵称是否已存在
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (existingUser != null) {
            throw new ApiException(ErrorCode.CONFLICT, "昵称已被使用，请换一个");
        }
        
        // 3. 创建用户
        User user = new User();
        user.setUsername(username);
        user.setAccessToken("token_" + UUID.randomUUID().toString().replace("-", ""));
        user.setIsActive(true);
        userMapper.insert(user);
        
        // 4. 原子标记邀请码已使用
        int updated = inviteCodeMapper.markUsedIfUnused(
                inviteCode, user.getId(), LocalDateTime.now());
        if (updated == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "邀请码已被使用");
        }
        
        // 5. 返回Token
        return Map.of(
            "userId", user.getId(),
            "username", user.getUsername(),
            "accessToken", user.getAccessToken(),
            "message", "注册成功"
        );
    }
}
