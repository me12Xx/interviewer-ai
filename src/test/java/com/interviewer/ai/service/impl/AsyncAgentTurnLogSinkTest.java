package com.interviewer.ai.service.impl;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AsyncAgentTurnLogSinkTest {

    @Test
    void persistenceFailureDoesNotReachCaller() throws InterruptedException {
        AgentTurnLogPersistenceService persistence = mock(AgentTurnLogPersistenceService.class);
        doThrow(new IllegalStateException("offline log failure")).when(persistence).persist(any());
        AsyncAgentTurnLogSink sink = new AsyncAgentTurnLogSink(persistence);

        sink.emit(new AgentTurnLogEvent(1L, "request", "trace", 1, 1,
                "ANSWER", null, "model", "change_topic", "EXECUTED", null,
                "VERY_FAMILIAR", null, null, null, 1L, null, null));

        Thread.sleep(100);
    }
}
