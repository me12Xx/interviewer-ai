package com.interviewer.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("interview_request")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InterviewRequest {

    @TableId(type = IdType.ASSIGN_ID)
    @EqualsAndHashCode.Include
    private Long id;
    private Long sessionId;
    private String requestId;
    private String status;
    private String responseJson;
    private String failureMessage;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
