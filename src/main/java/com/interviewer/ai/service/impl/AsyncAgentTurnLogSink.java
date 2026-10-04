package com.interviewer.ai.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class AsyncAgentTurnLogSink implements AgentTurnLogSink {

    private static final Logger log = LoggerFactory.getLogger(AsyncAgentTurnLogSink.class);
    private final AgentTurnLogPersistenceService persistence;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256),
            new ThreadPoolExecutor.DiscardPolicy());

    public AsyncAgentTurnLogSink(AgentTurnLogPersistenceService persistence) {
        this.persistence = persistence;
    }

    @Override
    public void emit(AgentTurnLogEvent event) {
        try {
            executor.execute(() -> persistSafely(event));
        } catch (RuntimeException rejected) {
            log.warn("[AgentTurnLog] 轨迹队列已满，丢弃事件 traceId={}", event.traceId());
        }
    }

    private void persistSafely(AgentTurnLogEvent event) {
        try {
            persistence.persist(event);
        } catch (RuntimeException failure) {
            log.warn("[AgentTurnLog] 轨迹写入失败，忽略日志错误 traceId={}", event.traceId(), failure);
        }
    }

}
