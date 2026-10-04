package com.interviewer.ai.service.impl;

import com.interviewer.ai.InterviewRequest;
import com.interviewer.ai.mapper.InterviewRequestMapper;
import com.interviewer.ai.mapper.InterviewSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InterviewRequestPersistenceService {

    private final InterviewRequestMapper requestMapper;
    private final InterviewSessionMapper sessionMapper;

    public InterviewRequestPersistenceService(InterviewRequestMapper requestMapper,
                                               InterviewSessionMapper sessionMapper) {
        this.requestMapper = requestMapper;
        this.sessionMapper = sessionMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InterviewRequest insertProcessing(Long sessionId, String requestId) {
        LocalDateTime now = LocalDateTime.now();
        InterviewRequest request = new InterviewRequest();
        request.setSessionId(sessionId);
        request.setRequestId(requestId);
        request.setStatus("PROCESSING");
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        requestMapper.insert(request);
        return request;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public InterviewRequest find(Long sessionId, String requestId) {
        return requestMapper.selectByRequestId(sessionId, requestId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean acquireLease(Long sessionId, String owner, LocalDateTime now, LocalDateTime expiresAt) {
        return sessionMapper.acquireLease(sessionId, owner, now, expiresAt) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLease(Long sessionId, String owner) {
        sessionMapper.releaseLease(sessionId, owner);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long id, String responseJson) {
        if (requestMapper.markCompleted(id, responseJson, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("幂等请求状态已变化，无法标记完成");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String failureMessage, LocalDateTime expiresAt) {
        requestMapper.markFailed(id, failureMessage, expiresAt, LocalDateTime.now());
    }
}
