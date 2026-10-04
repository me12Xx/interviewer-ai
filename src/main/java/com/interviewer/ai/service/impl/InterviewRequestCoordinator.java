package com.interviewer.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewer.ai.InterviewRequest;
import com.interviewer.ai.exception.ApiException;
import com.interviewer.ai.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class InterviewRequestCoordinator {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration FAILED_RETENTION = Duration.ofHours(24);
    private static final int FAILURE_MESSAGE_MAX_LENGTH = 500;

    private final InterviewRequestPersistenceService persistence;
    private final ObjectMapper objectMapper;

    public InterviewRequestCoordinator(InterviewRequestPersistenceService persistence,
                                       ObjectMapper objectMapper) {
        this.persistence = persistence;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> execute(Long sessionId, String requestId,
                                       Supplier<Map<String, Object>> action) {
        InterviewRequest request;
        try {
            request = persistence.insertProcessing(sessionId, requestId);
        } catch (DuplicateKeyException duplicate) {
            return handleDuplicate(sessionId, requestId);
        }

        String leaseOwner = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        if (!persistence.acquireLease(sessionId, leaseOwner, now, now.plus(LEASE_DURATION))) {
            persistence.markFailed(request.getId(), "会话正在处理其他请求", now.plus(FAILED_RETENTION));
            throw new ApiException(ErrorCode.CONFLICT, "会话正在处理其他请求，请稍后使用新的 requestId 重试");
        }

        try {
            Map<String, Object> response = action.get();
            persistence.markCompleted(request.getId(), writeResponse(response));
            return response;
        } catch (RuntimeException failure) {
            persistence.markFailed(request.getId(), failureMessage(failure),
                    LocalDateTime.now().plus(FAILED_RETENTION));
            throw failure;
        } finally {
            persistence.releaseLease(sessionId, leaseOwner);
        }
    }

    private Map<String, Object> handleDuplicate(Long sessionId, String requestId) {
        InterviewRequest existing = persistence.find(sessionId, requestId);
        if (existing == null) {
            throw new ApiException(ErrorCode.CONFLICT, "相同请求正在处理");
        }
        if ("COMPLETED".equals(existing.getStatus())) {
            return readResponse(existing.getResponseJson());
        }
        if ("FAILED".equals(existing.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "该 requestId 的请求已失败，请生成新的 requestId 重试");
        }
        throw new ApiException(ErrorCode.CONFLICT, "相同请求正在处理");
    }

    private String writeResponse(Map<String, Object> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存幂等响应", e);
        }
    }

    private Map<String, Object> readResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法读取幂等响应", e);
        }
    }

    private String failureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), FAILURE_MESSAGE_MAX_LENGTH));
    }
}
