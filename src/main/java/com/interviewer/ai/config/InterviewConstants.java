package com.interviewer.ai.config;

import java.util.Map;

/**
 * V2 面试常量定义
 */
public class InterviewConstants {
    
    /** 简历最大长度（字符数） */
    public static final int RESUME_MAX_LENGTH = 2000;

    /** 单次回答最大长度（Java 字符数） */
    public static final int ANSWER_MAX_LENGTH = 5000;
    
    /** 硬上限：最大轮次（达到则强制结束，防止面试永不结束） */
    public static final int MAX_ROUNDS_HARD = 15;
    
    /** 连续 NOT_FAMILIAR 次数上限（达到则强制结束） */
    public static final int MAX_NOT_FAMILIAR_STREAK = 2;
    
    /** 同一话题最多追问次数 */
    public static final int MAX_FOLLOWUP_PER_TOPIC = 3;
    
    /** 各阶段通过分数线 */
    public static final Map<Integer, Double> PASS_SCORES = Map.of(
        1, 6.0,   // 一面：基础 + 简历
        2, 6.5,   // 二面：深度 + 系统设计
        3, 7.0    // 三面：架构 + 综合
    );
    
    /** 熟悉度等级对应分数 */
    public static final Map<String, Integer> PROFICIENCY_SCORES = Map.of(
        "VERY_FAMILIAR", 10,
        "FAMILIAR_BUT_SHALLOW", 6,
        "NOT_FAMILIAR", 2
    );
    
    private InterviewConstants() {
        // 工具类，禁止实例化
    }
}
