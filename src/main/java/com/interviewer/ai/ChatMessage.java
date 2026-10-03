package com.interviewer.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("chat_message")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    @EqualsAndHashCode.Include
    private Long id;

    private Long sessionId;
    private String role;
    
    @ToString.Exclude
    private String content;
    
    private Integer roundNum;
    private LocalDateTime createdAt;

    private String proficiencyLevel;

    @ToString.Exclude
    private String evaluationReason;
}