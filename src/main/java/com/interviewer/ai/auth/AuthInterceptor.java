package com.interviewer.ai.auth;

import com.interviewer.ai.User;
import com.interviewer.ai.exception.ApiException;
import com.interviewer.ai.exception.ErrorCode;
import com.interviewer.ai.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    private final UserMapper userMapper;
    
    public AuthInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                            Object handler) {
        String uri = request.getRequestURI();
        
        // 管理员接口跳过
        if (uri.startsWith("/api/admin")) {
            return true;
        }
        
        // 注册接口跳过（未登录用户需要访问）
        if (uri.startsWith("/api/register")) {
            return true;
        }
        
        // 提取Token
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未登录");
        }
        
        // 验证Token
        User user = userMapper.selectByToken(token);
        if (user == null) {
            log.warn("无效Token");
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Token 无效");
        }
        
        // 存入上下文
        UserContext.setCurrentUserId(user.getId());
        log.debug("用户 {} 已认证", user.getUsername());
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        // 关键：防止内存泄漏
        UserContext.clear();
    }
}
