package com.interviewer.ai.service.impl;

interface AgentTurnLogSink {

    void emit(AgentTurnLogEvent event);
}
