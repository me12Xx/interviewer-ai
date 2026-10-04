package com.interviewer.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("interview_session")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InterviewSession {

    @TableId(type = IdType.ASSIGN_ID)
    @EqualsAndHashCode.Include
    private Long id;

    private Long userId;

    @ToString.Exclude
    private String resumeText;

    private String targetPosition;
    private String position;  // 岗位模板键：java / ai
    private String status;
    private Integer currentRound;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    private Integer interviewStage;
    private Integer attemptNumber;
    private Boolean isLatest;
    private Boolean isArchived;
    private Long previousSessionId;
    private BigDecimal finalScore;
    private String leaseOwner;
    private LocalDateTime leaseExpiresAt;

    public InterviewSession() {}

}
