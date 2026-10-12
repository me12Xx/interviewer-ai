package com.interviewer.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请码实体
 */
@TableName("invite_code")
@Data
public class InviteCode {
    
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private String code;
    private Boolean isUsed;
    private Long usedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;
    private Integer accessDays;
}
