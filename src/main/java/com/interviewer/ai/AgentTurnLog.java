package com.interviewer.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("agent_turn_log")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AgentTurnLog {

    @TableId(type = IdType.ASSIGN_ID)
    @EqualsAndHashCode.Include
    private Long id;
    private Long sessionId;
    private String requestId;
    private String traceId;
    private Integer roundNum;
    private Integer turnSeq;
    private String contextType;
    private String promptVersion;
    private String model;
    private String toolName;
    private String decision;
    private String finishReason;
    private String proficiencyLevel;
    private String degradeReason;
    private Integer tokenIn;
    private Integer tokenOut;
    private Long latencyMs;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
}
