package com.interviewer.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewer.ai.Assessment;
import com.interviewer.ai.ChatMessage;
import com.interviewer.ai.InterviewSession;
import com.interviewer.ai.Question;
import com.interviewer.ai.client.dto.ChatResponse;
import com.interviewer.ai.config.InterviewConstants;
import com.interviewer.ai.exception.ApiException;
import com.interviewer.ai.exception.ErrorCode;
import com.interviewer.ai.mapper.AssessmentMapper;
import com.interviewer.ai.mapper.ChatMessageMapper;
import com.interviewer.ai.mapper.InterviewSessionMapper;
import com.interviewer.ai.mapper.QuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final InterviewSessionMapper sessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final QuestionMapper questionMapper;
    private final AssessmentMapper assessmentMapper;
    private final ObjectMapper objectMapper;
    private final AgentTurnLogSink turnLogSink;
    private final AgentMetrics agentMetrics;

    public ToolExecutor(InterviewSessionMapper sessionMapper,
                        ChatMessageMapper chatMessageMapper,
                         QuestionMapper questionMapper,
                         AssessmentMapper assessmentMapper,
                         ObjectMapper objectMapper) {
        this(sessionMapper, chatMessageMapper, questionMapper, assessmentMapper, objectMapper,
                NoOpAgentTurnLogSink.INSTANCE, new AgentMetrics(new SimpleMeterRegistry()));
    }

    public ToolExecutor(InterviewSessionMapper sessionMapper,
                        ChatMessageMapper chatMessageMapper,
                        QuestionMapper questionMapper,
                        AssessmentMapper assessmentMapper,
                        ObjectMapper objectMapper,
                        AgentTurnLogSink turnLogSink) {
        this(sessionMapper, chatMessageMapper, questionMapper, assessmentMapper, objectMapper,
                turnLogSink, new AgentMetrics(new SimpleMeterRegistry()));
    }

    @Autowired
    public ToolExecutor(InterviewSessionMapper sessionMapper,
                         ChatMessageMapper chatMessageMapper,
                         QuestionMapper questionMapper,
                          AssessmentMapper assessmentMapper,
                          ObjectMapper objectMapper,
                          AgentTurnLogSink turnLogSink,
                          AgentMetrics agentMetrics) {
        this.sessionMapper = sessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.questionMapper = questionMapper;
        this.assessmentMapper = assessmentMapper;
        this.objectMapper = objectMapper;
        this.turnLogSink = turnLogSink;
        this.agentMetrics = agentMetrics;
    }

    @Transactional(rollbackFor = Exception.class)
    public ToolExecutionResult execute(InterviewSession session,
                                       ChatResponse.ToolCall toolCall,
                                       ToolExecutionContext context) {
        return execute(session, toolCall, context, null, String.valueOf(session.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public ToolExecutionResult execute(InterviewSession session,
                                       ChatResponse.ToolCall toolCall,
                                       ToolExecutionContext context,
                                       String requestId,
                                       String traceId) {
        if (toolCall == null || toolCall.getFunction() == null) {
            return completeExecution(session, null, context, requestId, traceId,
                    rejected(ToolExecutionResult.ToolRejectionReason.MISSING_TOOL_NAME,
                            "工具调用缺少 function"));
        }

        String toolName = toolCall.getFunction().getName();
        if (toolName == null || toolName.isBlank()) {
            return completeExecution(session, toolName, context, requestId, traceId,
                    rejected(ToolExecutionResult.ToolRejectionReason.MISSING_TOOL_NAME, "工具名为空"));
        }
        if (context == ToolExecutionContext.FIRST_QUESTION && "finish_interview".equals(toolName)) {
            return completeExecution(session, toolName, context, requestId, traceId,
                    rejected(ToolExecutionResult.ToolRejectionReason.INVALID_FIRST_QUESTION_TOOL,
                            "首题阶段不允许结束面试"));
        }

        Map<String, Object> arguments;
        try {
            String argumentsJson = toolCall.getFunction().getArguments();
            if (argumentsJson == null || argumentsJson.isBlank()) {
                return completeExecution(session, toolName, context, requestId, traceId,
                        rejected(ToolExecutionResult.ToolRejectionReason.INVALID_ARGUMENTS_JSON,
                                "arguments 为空"));
            }
            arguments = objectMapper.readValue(argumentsJson, new TypeReference<>() { });
        } catch (Exception e) {
            log.warn("[Tool] arguments 解析失败, sessionId={}, toolName={}", session.getId(), toolName, e);
            return completeExecution(session, toolName, context, requestId, traceId,
                    rejected(ToolExecutionResult.ToolRejectionReason.INVALID_ARGUMENTS_JSON,
                            "arguments 不是合法 JSON 对象"));
        }

        ToolExecutionResult result = switch (toolName) {
            case "ask_followup" -> executeAskFollowup(session, arguments, context);
            case "change_topic" -> executeChangeTopic(session, arguments, context);
            case "finish_interview" -> executeFinishInterview(session, arguments);
            default -> rejected(ToolExecutionResult.ToolRejectionReason.UNKNOWN_TOOL,
                    "未知工具: " + toolName);
        };
        return completeExecution(session, toolName, context, requestId, traceId, result);
    }

    private ToolExecutionResult completeExecution(InterviewSession session, String toolName,
                                                   ToolExecutionContext context, String requestId,
                                                   String traceId, ToolExecutionResult result) {
        emitExecutionLog(session, toolName, context, requestId, traceId, result);
        String decision = result instanceof ToolExecutionResult.Rejected ? "REJECTED" : "EXECUTED";
        String proficiency = result instanceof ToolExecutionResult.NextQuestion next
                ? next.proficiencyLevel() : null;
        agentMetrics.recordToolExecution(context.name(), decision, toolName, proficiency);
        return result;
    }

    private void emitExecutionLog(InterviewSession session, String toolName,
                                  ToolExecutionContext context, String requestId,
                                  String traceId, ToolExecutionResult result) {
        String decision = result instanceof ToolExecutionResult.Rejected ? "REJECTED" : "EXECUTED";
        String errorCode = result instanceof ToolExecutionResult.Rejected rejected
                ? rejected.reason().name() : null;
        String proficiency = result instanceof ToolExecutionResult.NextQuestion next
                ? next.proficiencyLevel() : null;
        try {
            turnLogSink.emit(new AgentTurnLogEvent(session.getId(), requestId, traceId,
                    session.getCurrentRound(), 0, context.name(), null, null, toolName, decision,
                    null, proficiency, null, null, null, null, errorCode, null));
        } catch (RuntimeException ignored) {
            log.warn("[AgentTurnLog] 工具执行轨迹提交失败，忽略日志错误 sessionId={}", session.getId());
        }
    }

    private ToolExecutionResult executeAskFollowup(InterviewSession session,
                                                    Map<String, Object> arguments,
                                                    ToolExecutionContext context) {
        String question = requiredString(arguments, "question");
        String reason = requiredString(arguments, "reason");
        String proficiency = normalizedString(arguments, "proficiency_level");
        ToolExecutionResult invalid = validateRequiredFields(question, reason, proficiency);
        if (invalid != null) {
            return invalid;
        }
        if (!"FAMILIAR_BUT_SHALLOW".equals(proficiency)) {
            return rejected(ToolExecutionResult.ToolRejectionReason.INVALID_PROFICIENCY,
                    "ask_followup 的 proficiency_level 非法");
        }

        int round = session.getCurrentRound();
        if (context == ToolExecutionContext.FIRST_QUESTION) {
            saveQuestion(session.getId(), question, round, null);
            saveAssistantMessage(session.getId(), question, round);
            return new ToolExecutionResult.NextQuestion(question, null);
        }

        long followupCount = countFollowups(session.getId(), round);
        if (followupCount >= InterviewConstants.MAX_FOLLOWUP_PER_TOPIC
                && round < InterviewConstants.MAX_ROUNDS_HARD) {
            saveEvaluation(session.getId(), proficiency, reason);
            return rejected(ToolExecutionResult.ToolRejectionReason.FOLLOWUP_LIMIT_REACHED,
                    "当前话题已达到追问上限，应切换话题");
        }

        saveEvaluation(session.getId(), proficiency, reason);
        if (followupCount >= InterviewConstants.MAX_FOLLOWUP_PER_TOPIC) {
            InterviewSession finished = markSessionFinished(session.getId());
            return new ToolExecutionResult.Finished(finishMessage(
                    "最后一个话题已完成最大追问次数，面试结束。", finished));
        }

        saveQuestion(session.getId(), question, round, "ask_followup");
        saveAssistantMessage(session.getId(), question, round);
        return new ToolExecutionResult.NextQuestion(question, proficiency);
    }

    private ToolExecutionResult executeChangeTopic(InterviewSession session,
                                                    Map<String, Object> arguments,
                                                    ToolExecutionContext context) {
        String question = requiredString(arguments, "question");
        String reason = requiredString(arguments, "reason");
        String proficiency = normalizedString(arguments, "proficiency_level");
        ToolExecutionResult invalid = validateRequiredFields(question, reason, proficiency);
        if (invalid != null) {
            return invalid;
        }
        if (!List.of("NOT_FAMILIAR", "VERY_FAMILIAR").contains(proficiency)) {
            return rejected(ToolExecutionResult.ToolRejectionReason.INVALID_PROFICIENCY,
                    "change_topic 的 proficiency_level 非法");
        }

        int currentRound = session.getCurrentRound();
        if (context == ToolExecutionContext.FIRST_QUESTION) {
            saveQuestion(session.getId(), question, currentRound, null);
            saveAssistantMessage(session.getId(), question, currentRound);
            return new ToolExecutionResult.NextQuestion(question, null);
        }

        saveEvaluation(session.getId(), proficiency, reason);
        if (isEliminated(session.getId())) {
            InterviewSession finished = markSessionFinished(session.getId());
            return new ToolExecutionResult.Finished(finishMessage(
                    "连续多次答不出核心问题，面试提前结束。", finished));
        }
        if (currentRound >= InterviewConstants.MAX_ROUNDS_HARD) {
            InterviewSession finished = markSessionFinished(session.getId());
            return new ToolExecutionResult.Finished(finishMessage(
                    "已达最大话题轮次，面试结束。", finished));
        }

        int nextRound = currentRound + 1;
        int updated = sessionMapper.update(null,
                new LambdaUpdateWrapper<InterviewSession>()
                        .eq(InterviewSession::getId, session.getId())
                        .eq(InterviewSession::getStatus, "IN_PROGRESS")
                        .eq(InterviewSession::getCurrentRound, currentRound)
                        .set(InterviewSession::getCurrentRound, nextRound));
        if (updated != 1) {
            throw new ApiException(ErrorCode.CONFLICT, "会话轮次已发生变化，请重试");
        }
        session.setCurrentRound(nextRound);

        saveQuestion(session.getId(), question, nextRound, "change_topic");
        saveAssistantMessage(session.getId(), question, nextRound);
        return new ToolExecutionResult.NextQuestion(question, proficiency);
    }

    private ToolExecutionResult executeFinishInterview(InterviewSession session,
                                                        Map<String, Object> arguments) {
        String summary = requiredString(arguments, "summary");
        if (summary == null) {
            return invalidField(arguments, "summary");
        }
        InterviewSession finished = markSessionFinished(session.getId());
        return new ToolExecutionResult.Finished(finishMessage("面试已结束。", finished));
    }

    private InterviewSession markSessionFinished(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        if (isFinished(session)) {
            return session;
        }
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "会话状态不允许结束: " + session.getStatus());
        }

        Integer stage = session.getInterviewStage();
        Double passScore = stage == null ? null : InterviewConstants.PASS_SCORES.get(stage);
        if (passScore == null) {
            throw new IllegalStateException("面试阶段或通过分数配置非法, sessionId=" + sessionId);
        }

        BigDecimal finalScore = calculateFinalScore(sessionId);
        String status = finalScore.compareTo(BigDecimal.valueOf(passScore)) >= 0 ? "PASSED" : "FAILED";
        LocalDateTime finishedAt = LocalDateTime.now();
        int updated = sessionMapper.update(null,
                new LambdaUpdateWrapper<InterviewSession>()
                        .eq(InterviewSession::getId, sessionId)
                        .eq(InterviewSession::getStatus, "IN_PROGRESS")
                        .set(InterviewSession::getStatus, status)
                        .set(InterviewSession::getFinalScore, finalScore)
                        .set(InterviewSession::getFinishedAt, finishedAt));

        InterviewSession persisted = updated == 0
                ? selectSessionForUpdate(sessionId)
                : sessionMapper.selectById(sessionId);
        if (persisted == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        if (updated == 0 && !isFinished(persisted)) {
            throw new ApiException(ErrorCode.CONFLICT, "会话结束状态更新冲突，请重试");
        }

        log.warn("[Finish] Session {} | Stage: {} | Score: {} | Status: {}",
                sessionId, stage, persisted.getFinalScore(), persisted.getStatus());
        return persisted;
    }

    private InterviewSession selectSessionForUpdate(Long sessionId) {
        return sessionMapper.selectOne(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getId, sessionId)
                        .last("FOR UPDATE"));
    }

    public boolean hasReachedFollowupLimit(Long sessionId, int round) {
        return countFollowups(sessionId, round) >= InterviewConstants.MAX_FOLLOWUP_PER_TOPIC;
    }

    private ToolExecutionResult validateRequiredFields(String question, String reason, String proficiency) {
        if (question == null) {
            return rejected(ToolExecutionResult.ToolRejectionReason.MISSING_REQUIRED_FIELD,
                    "question 缺失、为空或类型错误");
        }
        if (reason == null) {
            return rejected(ToolExecutionResult.ToolRejectionReason.MISSING_REQUIRED_FIELD,
                    "reason 缺失、为空或类型错误");
        }
        if (proficiency == null) {
            return rejected(ToolExecutionResult.ToolRejectionReason.MISSING_REQUIRED_FIELD,
                    "proficiency_level 缺失、为空或类型错误");
        }
        return null;
    }

    private ToolExecutionResult invalidField(Map<String, Object> arguments, String field) {
        Object value = arguments.get(field);
        ToolExecutionResult.ToolRejectionReason reason = value == null
                ? ToolExecutionResult.ToolRejectionReason.MISSING_REQUIRED_FIELD
                : ToolExecutionResult.ToolRejectionReason.INVALID_FIELD_TYPE;
        return rejected(reason, field + " 缺失、为空或类型错误");
    }

    private ToolExecutionResult rejected(ToolExecutionResult.ToolRejectionReason reason, String diagnostic) {
        return new ToolExecutionResult.Rejected(reason, diagnostic);
    }

    private String requiredString(Map<String, Object> arguments, String field) {
        Object value = arguments.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private String normalizedString(Map<String, Object> arguments, String field) {
        String value = requiredString(arguments, field);
        return value == null ? null : value.toUpperCase();
    }

    private void saveEvaluation(Long sessionId, String proficiency, String reason) {
        ChatMessage lastUser = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getRole, "user")
                        .isNull(ChatMessage::getProficiencyLevel)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 1"));
        if (lastUser == null) {
            throw new IllegalStateException("未找到待评价的用户回答, sessionId=" + sessionId);
        }
        lastUser.setProficiencyLevel(proficiency);
        lastUser.setEvaluationReason(reason);
        chatMessageMapper.updateById(lastUser);

        Question lastQuestion = questionMapper.selectOne(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getSessionId, sessionId)
                        .orderByDesc(Question::getId)
                        .last("LIMIT 1"));
        if (lastQuestion == null) {
            throw new IllegalStateException("未找到当前问题, sessionId=" + sessionId);
        }

        Assessment assessment = new Assessment();
        assessment.setSessionId(sessionId);
        assessment.setQuestionId(lastQuestion.getId());
        assessment.setScore(BigDecimal.valueOf(InterviewConstants.PROFICIENCY_SCORES.get(proficiency)));
        assessment.setFeedback(reason);
        assessment.setWeakPoints("");
        assessment.setCreatedAt(LocalDateTime.now());
        assessmentMapper.insert(assessment);
    }

    private long countFollowups(Long sessionId, int round) {
        Long count = questionMapper.selectCount(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getSessionId, sessionId)
                        .eq(Question::getRoundNum, round)
                        .eq(Question::getToolUsed, "ask_followup"));
        return count == null ? 0 : count;
    }

    private void saveQuestion(Long sessionId, String content, int round, String toolUsed) {
        Question question = new Question();
        question.setSessionId(sessionId);
        question.setContent(content);
        question.setRoundNum(round);
        question.setToolUsed(toolUsed);
        questionMapper.insert(question);
    }

    private void saveAssistantMessage(Long sessionId, String content, int round) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("assistant");
        message.setContent(content);
        message.setRoundNum(round);
        chatMessageMapper.insert(message);
    }

    private boolean isEliminated(Long sessionId) {
        int limit = InterviewConstants.MAX_NOT_FAMILIAR_STREAK;
        List<ChatMessage> recent = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getRole, "user")
                        .isNotNull(ChatMessage::getProficiencyLevel)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + limit));
        return recent.size() >= limit
                && recent.stream().allMatch(message -> "NOT_FAMILIAR".equals(message.getProficiencyLevel()));
    }

    private BigDecimal calculateFinalScore(Long sessionId) {
        List<ChatMessage> evaluatedMessages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getRole, "user")
                        .isNotNull(ChatMessage::getProficiencyLevel));
        if (evaluatedMessages.isEmpty()) {
            return BigDecimal.valueOf(5.0);
        }

        int totalScore = 0;
        int count = 0;
        for (ChatMessage message : evaluatedMessages) {
            Integer score = InterviewConstants.PROFICIENCY_SCORES.get(message.getProficiencyLevel());
            if (score != null) {
                totalScore += score;
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.valueOf(5.0);
        }
        return BigDecimal.valueOf(totalScore)
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private boolean isFinished(InterviewSession session) {
        return "PASSED".equals(session.getStatus()) || "FAILED".equals(session.getStatus());
    }

    private String finishMessage(String reason, InterviewSession session) {
        return String.format("%s 最终得分：%.1f，状态：%s",
                reason, session.getFinalScore().doubleValue(), session.getStatus());
    }
}
