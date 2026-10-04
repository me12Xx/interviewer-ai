package com.interviewer.ai.service.impl;

record AgentTurnLogEvent(
        Long sessionId,
        String requestId,
        String traceId,
        Integer roundNum,
        Integer turnSeq,
        String contextType,
        String promptVersion,
        String model,
        String toolName,
        String decision,
        String finishReason,
        String proficiencyLevel,
        String degradeReason,
        Integer tokenIn,
        Integer tokenOut,
        Long latencyMs,
        String errorCode,
        String errorMessage) {
}
