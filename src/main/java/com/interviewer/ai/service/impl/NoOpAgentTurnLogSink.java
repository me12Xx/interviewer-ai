package com.interviewer.ai.service.impl;

final class NoOpAgentTurnLogSink implements AgentTurnLogSink {

    static final AgentTurnLogSink INSTANCE = new NoOpAgentTurnLogSink();

    private NoOpAgentTurnLogSink() {
    }

    @Override
    public void emit(AgentTurnLogEvent event) {
    }
}
