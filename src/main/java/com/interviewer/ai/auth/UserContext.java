package com.interviewer.ai.auth;

import com.interviewer.ai.exception.ApiException;
import com.interviewer.ai.exception.ErrorCode;

public class UserContext {
    
    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();
    
    public static void setCurrentUserId(Long userId) {
        currentUserId.set(userId);
    }
    
    public static Long getCurrentUserId() {
        Long userId = currentUserId.get();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未登录");
        }
        return userId;
    }
    
    public static void clear() {
        currentUserId.remove();
    }
}
