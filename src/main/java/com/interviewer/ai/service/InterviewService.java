package com.interviewer.ai.service;

import com.interviewer.ai.InterviewSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InterviewService {

    InterviewSession startSession(
            Long userId,
            String resumeText,
            String targetPosition,
            Integer stage,
            String position
    );

    String getFirstQuestion(Long sessionId);

    Map<String, Object> answer(Long sessionId, String userAnswer, String requestId);

    Map<String, Object> getReport(Long sessionId);
    
    /**
     * 基于当前会话进入下一轮（校验归属 + 通过状态 + 轮次上限）。
     *
     * @param previousSessionId 已完成且通过的会话ID
     * @return 新建的下一轮会话
     */
    InterviewSession prepareNextStage(Long previousSessionId);

    /**
     * 重新开始本轮面试（校验归属后创建新的尝试）。
     *
     * @param sessionId 会话ID
     * @return 新建的会话
     */
    InterviewSession prepareRetry(Long sessionId);
    
    BigDecimal getBestScore(Long userId, Integer stage);

    /**
     * 获取会话的逐题评估详情
     *
     * @param sessionId 会话ID
     * @return 评估列表（包含问题内容、评分、反馈、薄弱点）
     */
    List<Map<String, Object>> getAssessments(Long sessionId);
}
