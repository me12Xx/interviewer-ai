package com.interviewer.ai.controller;

import com.interviewer.ai.InviteCode;
import com.interviewer.ai.User;
import com.interviewer.ai.exception.ApiException;
import com.interviewer.ai.exception.ErrorCode;
import com.interviewer.ai.mapper.InviteCodeMapper;
import com.interviewer.ai.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    @Value("${admin.token}")
    private String adminToken;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    
    private final UserMapper userMapper;
    private final InviteCodeMapper inviteCodeMapper;
    
    public AdminController(UserMapper userMapper, InviteCodeMapper inviteCodeMapper) {
        this.userMapper = userMapper;
        this.inviteCodeMapper = inviteCodeMapper;
    }
    
    private void checkAdmin(String token) {
        if (!adminToken.equals(token)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无权限");
        }
    }
    
    // ========== 批量生成邀请码 ==========
    
    /**
     * 批量生成邀请码
     * 请求示例：
     * POST /api/admin/invite-codes
     * Header: Admin-Token: <ADMIN_TOKEN>
     * Body: count=100&accessDays=7；accessDays=0 表示永不过期
     */
    @PostMapping("/invite-codes")
    public Map<String, Object> generateInviteCodes(
            @RequestHeader("Admin-Token") String token,
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "7") int accessDays) {
        
        checkAdmin(token);
        
        if (count < 1 || count > 500) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT, "数量必须在1-500之间");
        }

        if (accessDays < 0 || accessDays > 3650) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT, "有效天数必须为0-3650，0表示永不过期");
        }
        
        List<String> links = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String code = "invite_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            
            InviteCode inviteCode = new InviteCode();
            inviteCode.setCode(code);
            inviteCode.setIsUsed(false);
            inviteCode.setAccessDays(accessDays == 0 ? null : accessDays);
            inviteCodeMapper.insert(inviteCode);
            
            links.add(baseUrl + "/?invite=" + code);
        }
        
        return Map.of(
            "total", count,
            "accessDays", accessDays,
            "links", links,
            "message", "批量生成成功，请将链接发送给群友"
        );
    }
    
    // ========== 原有方法保持不变 ==========
    
    /**
     * 手动创建用户（已被邀请码注册替代，保留以备后用）
     *
     * @deprecated 推荐使用邀请码注册流程（POST /api/admin/invite-codes）
     */
    @Deprecated
    @PostMapping("/users")
    public Map<String, Object> createUser(
            @RequestHeader("Admin-Token") String token,
            @RequestParam String username) {
        
        checkAdmin(token);
        
        User user = new User();
        user.setUsername(username);
        user.setAccessToken("token_" + UUID.randomUUID().toString().replace("-", ""));
        user.setIsActive(true);
        userMapper.insert(user);
        
        return Map.of(
            "userId", user.getId(),
            "username", user.getUsername(),
            "accessToken", user.getAccessToken()
        );
    }
    
    /**
     * 禁用用户（保留以备后用）
     *
     * @deprecated 暂未启用，未来可能需要
     */
    @Deprecated
    @DeleteMapping("/users/{userId}")
    public Map<String, Object> disableUser(
            @RequestHeader("Admin-Token") String token,
            @PathVariable Long userId) {
        
        checkAdmin(token);
        
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        
        user.setIsActive(false);
        userMapper.updateById(user);
        
        return Map.of("message", "用户已禁用");
    }
}
