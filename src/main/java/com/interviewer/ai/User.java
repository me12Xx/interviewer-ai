package com.interviewer.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@TableName("user")
@Data
public class User {
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private String username;
    private String accessToken;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime accessExpiresAt;
}
