package com.interviewer.ai.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
class AgentMetrics {

    static final String MODEL_CALLS = "interviewer.ai.model.calls";
    static final String MODEL_LATENCY = "interviewer.ai.model.latency";
    static final String MODEL_TOKENS = "interviewer.ai.model.tokens";
    static final String TOOL_EXECUTIONS = "interviewer.ai.tool.executions";

    private static final Logger log = LoggerFactory.getLogger(AgentMetrics.class);
    private static final String NONE = "none";
    private static final Set<String> TOOL_NAMES = Set.of(
            "ask_followup", "change_topic", "finish_interview");

    private final MeterRegistry registry;

    AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void recordModelCall(String context, String decision, String toolName,
                         long latencyNanos, Integer tokenIn, Integer tokenOut) {
        try {
            String[] tags = {"context", tag(context), "decision", tag(decision),
                    "tool", toolTag(toolName)};
            Counter.builder(MODEL_CALLS).tags(tags).register(registry).increment();
            Timer.builder(MODEL_LATENCY).tags(tags).register(registry)
                    .record(Duration.ofNanos(Math.max(0, latencyNanos)));
            incrementTokens("input", context, tokenIn);
            incrementTokens("output", context, tokenOut);
        } catch (RuntimeException e) {
            log.warn("[Metrics] 模型调用指标记录失败，忽略指标错误");
        }
    }

    void recordToolExecution(String context, String decision, String toolName, String proficiency) {
        try {
            Counter.builder(TOOL_EXECUTIONS)
                    .tags("context", tag(context), "decision", tag(decision),
                            "tool", toolTag(toolName), "proficiency", tag(proficiency))
                    .register(registry)
                    .increment();
        } catch (RuntimeException e) {
            log.warn("[Metrics] 工具执行指标记录失败，忽略指标错误");
        }
    }

    private void incrementTokens(String direction, String context, Integer tokens) {
        if (tokens != null && tokens > 0) {
            Counter.builder(MODEL_TOKENS)
                    .tags("direction", direction, "context", tag(context))
                    .register(registry)
                    .increment(tokens);
        }
    }

    private String tag(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }

    private String toolTag(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return NONE;
        }
        return TOOL_NAMES.contains(toolName) ? toolName : "unknown";
    }
}
