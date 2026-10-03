package com.interviewer.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("question")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Question {

    @TableId(type = IdType.ASSIGN_ID)
    @EqualsAndHashCode.Include
    private Long id;

    private Long sessionId;
    
    @ToString.Exclude
    private String content;
    
    private String category;
    private Integer roundNum;
    private String toolUsed;
    private LocalDateTime createdAt;
}