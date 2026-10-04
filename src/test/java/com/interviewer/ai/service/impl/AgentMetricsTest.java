package com.interviewer.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewer.ai.InterviewSession;
import com.interviewer.ai.client.dto.ChatResponse;
import com.interviewer.ai.mapper.AssessmentMapper;
import com.interviewer.ai.mapper.ChatMessageMapper;
import com.interviewer.ai.mapper.InterviewSessionMapper;
import com.interviewer.ai.mapper.QuestionMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentMetricsTest {

    @Test
    void recordsModelCallLatencyAndTokens() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetrics metrics = new AgentMetrics(registry);

        metrics.recordModelCall("ANSWER", "ACCEPTED", "change_topic",
                TimeUnit.MILLISECONDS.toNanos(125), 40, 12);

        assertEquals(1.0, registry.get(AgentMetrics.MODEL_CALLS)
                .tags("context", "ANSWER", "decision", "ACCEPTED", "tool", "change_topic")
                .counter().count());
        assertEquals(1, registry.get(AgentMetrics.MODEL_LATENCY)
                .tags("context", "ANSWER", "decision", "ACCEPTED", "tool", "change_topic")
                .timer().count());
        assertEquals(125.0, registry.get(AgentMetrics.MODEL_LATENCY)
                .tags("context", "ANSWER", "decision", "ACCEPTED", "tool", "change_topic")
                .timer().totalTime(TimeUnit.MILLISECONDS), 0.001);
        assertEquals(40.0, registry.get(AgentMetrics.MODEL_TOKENS)
                .tags("direction", "input", "context", "ANSWER").counter().count());
        assertEquals(12.0, registry.get(AgentMetrics.MODEL_TOKENS)
                .tags("direction", "output", "context", "ANSWER").counter().count());
    }

    @Test
    void normalizesUnknownToolNamesToBoundCardinality() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetrics metrics = new AgentMetrics(registry);

        metrics.recordModelCall("ANSWER", "DEGRADED", "invented-tool-123",
                1, null, null);

        assertEquals(1.0, registry.get(AgentMetrics.MODEL_CALLS)
                .tags("context", "ANSWER", "decision", "DEGRADED", "tool", "unknown")
                .counter().count());
    }

    @Test
    void toolExecutorRecordsRejectedExecution() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetrics metrics = new AgentMetrics(registry);
        ToolExecutor executor = new ToolExecutor(
                mock(InterviewSessionMapper.class),
                mock(ChatMessageMapper.class),
                mock(QuestionMapper.class),
                mock(AssessmentMapper.class),
                new ObjectMapper(),
                NoOpAgentTurnLogSink.INSTANCE,
                metrics);
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setCurrentRound(1);

        ChatResponse.FunctionCall function = new ChatResponse.FunctionCall();
        function.setName("change_topic");
        function.setArguments(new ObjectMapper().writeValueAsString(Map.of(
                "question", "下一题",
                "reason", "评价",
                "proficiency_level", "FAMILIAR_BUT_SHALLOW")));
        ChatResponse.ToolCall toolCall = new ChatResponse.ToolCall();
        toolCall.setFunction(function);

        executor.execute(session, toolCall, ToolExecutionContext.ANSWER);

        assertEquals(1.0, registry.get(AgentMetrics.TOOL_EXECUTIONS)
                .tags("context", "ANSWER", "decision", "REJECTED",
                        "tool", "change_topic", "proficiency", "none")
                .counter().count());
    }
}
