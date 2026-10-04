package com.interviewer.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interviewer.ai.ChatMessage;
import com.interviewer.ai.InterviewRequest;
import com.interviewer.ai.InterviewSession;
import com.interviewer.ai.exception.ApiException;
import com.interviewer.ai.exception.ErrorCode;
import com.interviewer.ai.mapper.ChatMessageMapper;
import com.interviewer.ai.mapper.InterviewRequestMapper;
import com.interviewer.ai.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "typesafe.enabled=false")
@Tag("mysql")
class InterviewRequestConcurrencyIntegrationTest {

    @Autowired
    private InterviewRequestCoordinator coordinator;

    @Autowired
    private InterviewRequestMapper requestMapper;

    @Autowired
    private InterviewSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Test
    void sameRequestIdIsExecutedOnceAndConcurrentDuplicateGetsConflict() throws Exception {
        InterviewSession session = createSession();
        String requestId = "concurrency-" + UUID.randomUUID();
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Map<String, Object>> first = executor.submit(() -> coordinator.execute(
                    session.getId(), requestId, () -> {
                        executions.incrementAndGet();
                        insertMessage(session.getId());
                        actionStarted.countDown();
                        await(releaseAction);
                        return Map.of("finished", false, "question", "next");
                    }));

            assertTrue(actionStarted.await(10, TimeUnit.SECONDS), "首请求未进入业务回调");

            Future<ApiException> duplicate = executor.submit(() -> {
                try {
                    coordinator.execute(session.getId(), requestId, () -> {
                        executions.incrementAndGet();
                        return Map.of();
                    });
                    throw new AssertionError("重复请求应被幂等约束拦截");
                } catch (ApiException conflict) {
                    return conflict;
                }
            });

            ApiException conflict = duplicate.get(10, TimeUnit.SECONDS);
            assertEquals(ErrorCode.CONFLICT, conflict.getErrorCode());
            assertEquals(409, conflict.getErrorCode().getHttpStatus().value());
            assertEquals("相同请求正在处理", conflict.getMessage());

            releaseAction.countDown();
            assertEquals("next", first.get(10, TimeUnit.SECONDS).get("question"));
            assertEquals(1, executions.get());

            List<InterviewRequest> rows = requestMapper.selectList(null).stream()
                    .filter(row -> session.getId().equals(row.getSessionId()))
                    .filter(row -> requestId.equals(row.getRequestId()))
                    .toList();
            assertEquals(1, rows.size());
            assertEquals("COMPLETED", rows.getFirst().getStatus());
            assertEquals(1L, chatMessageMapper.selectCount(
                    new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, session.getId())));
        } finally {
            releaseAction.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            InterviewRequest request = requestMapper.selectByRequestId(session.getId(), requestId);
            if (request != null) {
                requestMapper.deleteById(request.getId());
            }
            chatMessageMapper.delete(
                    new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, session.getId()));
            sessionMapper.deleteById(session.getId());
        }
    }

    private InterviewSession createSession() {
        InterviewSession session = new InterviewSession();
        session.setResumeText("concurrency integration test");
        session.setTargetPosition("Java backend");
        session.setPosition("java");
        session.setStatus("IN_PROGRESS");
        session.setCurrentRound(1);
        session.setInterviewStage(1);
        session.setAttemptNumber(1);
        session.setIsLatest(true);
        session.setIsArchived(false);
        assertEquals(1, sessionMapper.insert(session));
        return session;
    }

    private void insertMessage(Long sessionId) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("user");
        message.setContent("concurrent answer");
        message.setRoundNum(1);
        assertEquals(1, chatMessageMapper.insert(message));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待并发测试放行超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试线程被中断", e);
        }
    }
}
