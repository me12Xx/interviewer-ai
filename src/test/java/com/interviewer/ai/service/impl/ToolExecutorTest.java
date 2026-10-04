package com.interviewer.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.interviewer.ai.ChatMessage;
import com.interviewer.ai.InterviewSession;
import com.interviewer.ai.Question;
import com.interviewer.ai.client.dto.ChatResponse;
import com.interviewer.ai.mapper.AssessmentMapper;
import com.interviewer.ai.mapper.ChatMessageMapper;
import com.interviewer.ai.mapper.InterviewSessionMapper;
import com.interviewer.ai.mapper.QuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class ToolExecutorTest {

    private InterviewSessionMapper sessionMapper;
    private ChatMessageMapper chatMessageMapper;
    private QuestionMapper questionMapper;
    private AssessmentMapper assessmentMapper;
    private AgentTurnLogSink turnLogSink;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(InterviewSession.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), "tool-executor-test"),
                    InterviewSession.class);
        }
        sessionMapper = mock(InterviewSessionMapper.class);
        chatMessageMapper = mock(ChatMessageMapper.class);
        questionMapper = mock(QuestionMapper.class);
        assessmentMapper = mock(AssessmentMapper.class);
        turnLogSink = mock(AgentTurnLogSink.class);
        toolExecutor = new ToolExecutor(
                sessionMapper,
                chatMessageMapper,
                questionMapper,
                assessmentMapper,
                new ObjectMapper(), turnLogSink);
    }

    @Test
    void firstQuestionRejectsFinishWithoutWriting() {
        ToolExecutionResult result = toolExecutor.execute(
                session(1, 1, "IN_PROGRESS"),
                toolCall("finish_interview", Map.of("summary", "结束")),
                ToolExecutionContext.FIRST_QUESTION);

        ToolExecutionResult.Rejected rejected = assertInstanceOf(ToolExecutionResult.Rejected.class, result);
        assertEquals(ToolExecutionResult.ToolRejectionReason.INVALID_FIRST_QUESTION_TOOL, rejected.reason());
        verify(sessionMapper, never()).update(any(), any());
        verify(questionMapper, never()).insert((Question) any());
        verify(chatMessageMapper, never()).insert((ChatMessage) any());
    }

    @Test
    void firstQuestionDoesNotCreateEvaluationOrAdvanceRound() {
        InterviewSession session = session(1, 2, "IN_PROGRESS");

        ToolExecutionResult result = toolExecutor.execute(
                session,
                toolCall("ask_followup", Map.of(
                        "question", "介绍一下 HashMap",
                        "reason", "首题",
                        "proficiency_level", "FAMILIAR_BUT_SHALLOW")),
                ToolExecutionContext.FIRST_QUESTION);

        ToolExecutionResult.NextQuestion next = assertInstanceOf(ToolExecutionResult.NextQuestion.class, result);
        assertEquals("介绍一下 HashMap", next.question());
        assertNull(next.proficiencyLevel());
        assertEquals(2, session.getCurrentRound());
        verify(questionMapper).insert(any(Question.class));
        verify(chatMessageMapper).insert(any(ChatMessage.class));
        verify(assessmentMapper, never()).insert((com.interviewer.ai.Assessment) any());
        verify(sessionMapper, never()).update(any(), any());
    }

    @Test
    void invalidProficiencyIsRejectedBeforeWriting() {
        ToolExecutionResult result = toolExecutor.execute(
                session(1, 1, "IN_PROGRESS"),
                toolCall("change_topic", Map.of(
                        "question", "下一个问题",
                        "reason", "评价",
                        "proficiency_level", "FAMILIAR_BUT_SHALLOW")),
                ToolExecutionContext.ANSWER);

        ToolExecutionResult.Rejected rejected = assertInstanceOf(ToolExecutionResult.Rejected.class, result);
        assertEquals(ToolExecutionResult.ToolRejectionReason.INVALID_PROFICIENCY, rejected.reason());
        verify(sessionMapper, never()).update(any(), any());
        verify(questionMapper, never()).insert((Question) any());
        verify(assessmentMapper, never()).insert((com.interviewer.ai.Assessment) any());
    }

    @Test
    void changeTopicAdvancesRoundOnce() {
        InterviewSession session = session(1, 1, "IN_PROGRESS");
        when(chatMessageMapper.selectOne(any())).thenReturn(userAnswer());
        when(questionMapper.selectOne(any())).thenReturn(currentQuestion());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(sessionMapper.update(any(), any())).thenReturn(1);

        ToolExecutionResult result = toolExecutor.execute(
                session,
                toolCall("change_topic", Map.of(
                        "question", "解释线程池拒绝策略",
                        "reason", "回答完整",
                        "proficiency_level", "VERY_FAMILIAR")),
                ToolExecutionContext.ANSWER);

        ToolExecutionResult.NextQuestion next = assertInstanceOf(ToolExecutionResult.NextQuestion.class, result);
        assertEquals("解释线程池拒绝策略", next.question());
        assertEquals("VERY_FAMILIAR", next.proficiencyLevel());
        assertEquals(2, session.getCurrentRound());
        verify(sessionMapper).update(any(), any());
        verify(questionMapper).insert(any(Question.class));
        verify(chatMessageMapper).insert(any(ChatMessage.class));
        verify(assessmentMapper).insert((com.interviewer.ai.Assessment) any());
    }

    @Test
    void successfulToolExecutionEmitsStructuredTrace() {
        InterviewSession session = session(1, 1, "IN_PROGRESS");
        when(chatMessageMapper.selectOne(any())).thenReturn(userAnswer());
        when(questionMapper.selectOne(any())).thenReturn(currentQuestion());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(sessionMapper.update(any(), any())).thenReturn(1);

        toolExecutor.execute(session, toolCall("change_topic", Map.of(
                "question", "解释线程池拒绝策略",
                "reason", "回答完整",
                "proficiency_level", "VERY_FAMILIAR")), ToolExecutionContext.ANSWER);

        ArgumentCaptor<AgentTurnLogEvent> captor = ArgumentCaptor.forClass(AgentTurnLogEvent.class);
        verify(turnLogSink).emit(captor.capture());
        AgentTurnLogEvent event = captor.getValue();
        assertEquals("change_topic", event.toolName());
        assertEquals("EXECUTED", event.decision());
        assertEquals("VERY_FAMILIAR", event.proficiencyLevel());
        assertEquals("ANSWER", event.contextType());
    }

    @Test
    void followupLimitRejectsWithoutCreatingAnotherQuestion() {
        when(chatMessageMapper.selectOne(any())).thenReturn(userAnswer());
        when(questionMapper.selectOne(any())).thenReturn(currentQuestion());
        when(questionMapper.selectCount(any())).thenReturn((long) Integer.MAX_VALUE);

        ToolExecutionResult result = toolExecutor.execute(
                session(1, 1, "IN_PROGRESS"),
                toolCall("ask_followup", Map.of(
                        "question", "继续说明实现细节",
                        "reason", "追问评价",
                        "proficiency_level", "FAMILIAR_BUT_SHALLOW")),
                ToolExecutionContext.ANSWER);

        ToolExecutionResult.Rejected rejected = assertInstanceOf(ToolExecutionResult.Rejected.class, result);
        assertEquals(ToolExecutionResult.ToolRejectionReason.FOLLOWUP_LIMIT_REACHED, rejected.reason());
        verify(assessmentMapper).insert((com.interviewer.ai.Assessment) any());
        verify(questionMapper, never()).insert((Question) any());
        verify(sessionMapper, never()).update(any(), any());
    }

    @Test
    void malformedArgumentsAreRejectedBeforeWriting() {
        ChatResponse.FunctionCall function = new ChatResponse.FunctionCall();
        function.setName("ask_followup");
        function.setArguments("not-json");
        ChatResponse.ToolCall call = new ChatResponse.ToolCall();
        call.setFunction(function);

        ToolExecutionResult result = toolExecutor.execute(
                session(1, 1, "IN_PROGRESS"), call, ToolExecutionContext.ANSWER);

        ToolExecutionResult.Rejected rejected = assertInstanceOf(ToolExecutionResult.Rejected.class, result);
        assertEquals(ToolExecutionResult.ToolRejectionReason.INVALID_ARGUMENTS_JSON, rejected.reason());
        verify(questionMapper, never()).insert((Question) any());
        verify(assessmentMapper, never()).insert((com.interviewer.ai.Assessment) any());
    }

    @Test
    void finishInterviewPersistsTerminalStateAndScore() {
        InterviewSession current = session(1, 15, "IN_PROGRESS");
        InterviewSession persisted = session(1, 15, "PASSED");
        persisted.setFinalScore(java.math.BigDecimal.valueOf(10.0));
        when(sessionMapper.selectById(1L)).thenReturn(current, persisted);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(evaluatedAnswer("VERY_FAMILIAR")));
        when(sessionMapper.update(any(), any())).thenReturn(1);

        ToolExecutionResult result = toolExecutor.execute(
                current,
                toolCall("finish_interview", Map.of("summary", "完成")),
                ToolExecutionContext.ANSWER);

        ToolExecutionResult.Finished finished = assertInstanceOf(ToolExecutionResult.Finished.class, result);
        assertNotNull(finished.message());
        verify(sessionMapper).update(any(), any());
        verify(questionMapper, never()).insert((Question) any());
    }

    private InterviewSession session(long id, int round, String status) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setCurrentRound(round);
        session.setInterviewStage(1);
        session.setStatus(status);
        return session;
    }

    private ChatResponse.ToolCall toolCall(String name, Map<String, Object> arguments) {
        ChatResponse.FunctionCall function = new ChatResponse.FunctionCall();
        function.setName(name);
        try {
            function.setArguments(new ObjectMapper().writeValueAsString(arguments));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        ChatResponse.ToolCall toolCall = new ChatResponse.ToolCall();
        toolCall.setFunction(function);
        return toolCall;
    }

    private ChatMessage userAnswer() {
        ChatMessage message = new ChatMessage();
        message.setId(10L);
        message.setSessionId(1L);
        message.setRole("user");
        message.setContent("回答");
        return message;
    }

    private ChatMessage evaluatedAnswer(String proficiency) {
        ChatMessage message = userAnswer();
        message.setProficiencyLevel(proficiency);
        return message;
    }

    private Question currentQuestion() {
        Question question = new Question();
        question.setId(20L);
        question.setSessionId(1L);
        question.setContent("当前问题");
        return question;
    }
}
