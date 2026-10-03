package com.interviewer.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("assessment")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Assessment {

    @TableId(type = IdType.ASSIGN_ID)
    @EqualsAndHashCode.Include
    private Long id;

    private Long sessionId;
    private Long questionId;
    private BigDecimal score;
    
    @ToString.Exclude
    private String feedback;
    
    @ToString.Exclude
    private String weakPoints;
    
    private LocalDateTime createdAt;
}