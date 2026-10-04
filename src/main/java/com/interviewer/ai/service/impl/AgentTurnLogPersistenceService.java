package com.interviewer.ai.service.impl;

import com.interviewer.ai.AgentTurnLog;
import com.interviewer.ai.mapper.AgentTurnLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AgentTurnLogPersistenceService {

    private final AgentTurnLogMapper mapper;

    public AgentTurnLogPersistenceService(AgentTurnLogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AgentTurnLogEvent event) {
        AgentTurnLog logEntry = new AgentTurnLog();
        logEntry.setSessionId(event.sessionId());
        logEntry.setRequestId(event.requestId());
        logEntry.setTraceId(event.traceId());
        logEntry.setRoundNum(event.roundNum());
        logEntry.setTurnSeq(event.turnSeq());
        logEntry.setContextType(event.contextType());
        logEntry.setPromptVersion(event.promptVersion());
        logEntry.setModel(event.model());
        logEntry.setToolName(event.toolName());
        logEntry.setDecision(event.decision());
        logEntry.setFinishReason(event.finishReason());
        logEntry.setProficiencyLevel(event.proficiencyLevel());
        logEntry.setDegradeReason(event.degradeReason());
        logEntry.setTokenIn(event.tokenIn());
        logEntry.setTokenOut(event.tokenOut());
        logEntry.setLatencyMs(event.latencyMs());
        logEntry.setErrorCode(event.errorCode());
        logEntry.setErrorMessage(event.errorMessage());
        logEntry.setCreatedAt(LocalDateTime.now());
        mapper.insert(logEntry);
    }
}
