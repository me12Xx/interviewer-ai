package com.interviewer.ai.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewer.ai.Assessment;
import com.interviewer.ai.ChatMessage;
import com.interviewer.ai.InterviewSession;
import com.interviewer.ai.Question;
import com.interviewer.ai.auth.UserContext;
import com.interviewer.ai.client.AiClient;
import com.interviewer.ai.client.dto.ChatResponse;
import com.interviewer.ai.config.PromptLoader;
import com.interviewer.ai.exception.AiServiceException;
import com.interviewer.ai.mapper.AssessmentMapper;
import com.interviewer.ai.mapper.ChatMessageMapper;
import com.interviewer.ai.mapper.InterviewSessionMapper;
import com.interviewer.ai.mapper.QuestionMapper;
import com.interviewer.ai.rag.DocumentChunk;
import com.interviewer.ai.rag.EmbeddingService;
import com.interviewer.ai.rag.KnowledgeBaseStorage;
import com.interviewer.ai.rag.RagService;
import com.interviewer.ai.service.TypeSafeClient;
import com.interviewer.ai.evaluation.RagEvaluationMetrics;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflineEvaluationCasesTest {

    private static final Long SESSION_ID = 101L;
    private static final Long USER_ID = 42L;
    private static final Path REPORT_PATH = Path.of("docs", "EVALUATION_REPORT.md");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Case> CASES = List.of(
            new Case("agent-first-finish", "首题拒绝结束工具", "ToolExecutor/FIRST_QUESTION"),
            new Case("agent-first-no-score", "首题不写熟悉度", "ToolExecutor/FIRST_QUESTION"),
            new Case("agent-invalid-proficiency", "非法熟悉度拒绝", "ToolExecutor/ANSWER"),
            new Case("agent-invalid-json", "非法 JSON 拒绝", "ToolExecutor/ANSWER"),
            new Case("agent-followup-limit", "追问达到上限", "ToolExecutor/ANSWER"),
            new Case("agent-change-round", "換題只推进一轮", "ToolExecutor/ANSWER"),
            new Case("agent-finish-terminal", "结束写入终态", "ToolExecutor/ANSWER"),
            new Case("agent-unknown-tool", "未知工具拒绝", "ToolExecutor/ANSWER"),
            new Case("agent-missing-field", "缺失必填字段拒绝", "ToolExecutor/ANSWER"),
            new Case("agent-wrong-type", "字段类型错误拒绝", "ToolExecutor/ANSWER"),
            new Case("agent-empty-arguments", "空 arguments 拒绝", "ToolExecutor/ANSWER"),
            new Case("agent-multiple-tools", "多工具调用纠正", "InterviewService.answer"),
            new Case("agent-text-without-tool", "纯文本纠正", "InterviewService.answer"),
            new Case("agent-correction-limit", "协议纠正耗尽降级", "InterviewService.answer"),
            new Case("agent-ai-error", "AI 异常非终结降级", "InterviewService.answer"),
            new Case("rag-java-isolation", "Java 岗位隔离", "RagService.retrieve"),
            new Case("rag-ai-isolation", "AI 岗位隔离", "RagService.retrieve"),
            new Case("rag-empty-position", "无岗位结果为空", "RagService.retrieve"),
            new Case("rag-topk-validation", "topK 非法拒绝", "RagService.retrieve"),
            new Case("rag-recall-at-3", "Recall@3 计算", "RagEvaluationMetrics.recallAt"),
            new Case("rag-recall-at-5", "Recall@5 计算", "RagEvaluationMetrics.recallAt"),
            new Case("rag-mrr", "MRR 计算", "RagEvaluationMetrics.mrr"),
            new Case("rag-rerank-fallback", "重排失败降级", "RagService.retrieve/Jev"),
            new Case("rag-batch-index", "Embedding index 还原", "EmbeddingService.embedBatch"),
            new Case("regression-round-sync", "换题后内存轮次同步", "ToolExecutor/change_topic"),
            new Case("regression-mybatis-cache", "Lambda 元数据初始化", "TableInfoHelper/ToolExecutor"),
            new Case("regression-mockito-agent", "测试 agent 启动配置", "Maven Surefire configuration"),
            new Case("regression-n-plus-one", "评估批量查询问题", "InterviewService.getAssessments"),
            new Case("regression-retry-query", "重试避免重复查询", "InterviewService.prepareRetry"),
            new Case("regression-no-terminal-degrade", "异常降级不结算", "InterviewService.answer")
    );

    private static final List<CaseResult> RESULTS = new ArrayList<>();
    private static final Map<String, Case> CASES_BY_ID = new HashMap<>();

    static {
        for (Case evaluationCase : CASES) {
            CASES_BY_ID.put(evaluationCase.id(), evaluationCase);
        }
    }

    @BeforeAll
    static void initializeMybatisMetadata() {
        if (TableInfoHelper.getTableInfo(InterviewSession.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), "offline-evaluation"),
                    InterviewSession.class);
        }
        if (TableInfoHelper.getTableInfo(ChatMessage.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), "offline-evaluation"),
                    ChatMessage.class);
        }
        if (TableInfoHelper.getTableInfo(Question.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), "offline-evaluation"),
                    Question.class);
        }
        if (TableInfoHelper.getTableInfo(Assessment.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), "offline-evaluation"),
                    Assessment.class);
        }
    }

    @AfterAll
    static void writeReport() throws IOException {
        Files.createDirectories(REPORT_PATH.getParent());
        String report = renderReport();
        Files.writeString(REPORT_PATH, report, StandardCharsets.UTF_8);
    }

    @Test
    void executesEveryOfflineCaseAndAssertsItsBehavior() {
        assertEquals(30, CASES.size(), "evaluation set should contain exactly 30 cases");
        for (Case evaluationCase : CASES) {
            executeAndAssert(evaluationCase);
        }
        assertEquals(CASES.size(), RESULTS.size(), "every case should produce an execution result");
        assertTrue(RESULTS.stream().allMatch(CaseResult::passed), renderReport());
    }

    private void executeAndAssert(Case evaluationCase) {
        try {
            if (evaluationCase.entryPoint().startsWith("InterviewService")) {
                UserContext.setCurrentUserId(USER_ID);
            }
            switch (evaluationCase.id()) {
                case "agent-first-finish" -> firstQuestionCannotFinish();
                case "agent-first-no-score" -> firstQuestionDoesNotScore();
                case "agent-invalid-proficiency" -> invalidProficiencyIsRejected();
                case "agent-invalid-json" -> malformedJsonIsRejected();
                case "agent-followup-limit" -> followupLimitIsEnforced();
                case "agent-change-round" -> changeTopicAdvancesRoundOnce();
                case "agent-finish-terminal" -> finishPersistsTerminalState();
                case "agent-unknown-tool" -> unknownToolIsRejected();
                case "agent-missing-field" -> missingFieldIsRejected();
                case "agent-wrong-type" -> wrongFieldTypeIsRejected();
                case "agent-empty-arguments" -> emptyArgumentsAreRejected();
                case "agent-multiple-tools" -> multipleToolCallsAreCorrected();
                case "agent-text-without-tool" -> textOnlyResponseIsCorrected();
                case "agent-correction-limit" -> correctionLimitDegradesWithoutFinishing();
                case "agent-ai-error" -> aiFailureDegradesWithoutFinishing();
                case "rag-java-isolation" -> positionRetrievalIsIsolated("java", "java-1");
                case "rag-ai-isolation" -> positionRetrievalIsIsolated("ai", "ai-1");
                case "rag-empty-position" -> unknownPositionReturnsNoResults();
                case "rag-topk-validation" -> invalidTopKIsRejected();
                case "rag-recall-at-3" -> recallAtThreeIsCorrect();
                case "rag-recall-at-5" -> recallAtFiveIsCorrect();
                case "rag-mrr" -> reciprocalRankIsCorrect();
                case "rag-rerank-fallback" -> rerankerFailureFallsBackToCosine();
                case "rag-batch-index" -> embeddingResponseIndexesRestoreInputOrder();
                case "regression-round-sync" -> changeTopicSynchronizesInMemoryRound();
                case "regression-mybatis-cache" -> mybatisMetadataSupportsLambdaExecution();
                case "regression-mockito-agent" -> surefireEnablesMockitoSelfAttach();
                case "regression-n-plus-one" -> assessmentsLoadQuestionsInOneBatch();
                case "regression-retry-query" -> retryUsesLoadedSessionOnce();
                case "regression-no-terminal-degrade" -> protocolFailureDoesNotSettleSession();
                default -> throw new AssertionError("No executor for case " + evaluationCase.id());
            }
            RESULTS.add(new CaseResult(evaluationCase.id(), evaluationCase.description(), true, "PASS", ""));
        } catch (Throwable failure) {
            RESULTS.add(new CaseResult(evaluationCase.id(), evaluationCase.description(), false,
                    failure.getClass().getSimpleName(), safeMessage(failure)));
        } finally {
            UserContext.clear();
        }
    }

    private void firstQuestionCannotFinish() {
        Fixture fixture = new Fixture();
        ToolExecutionResult result = fixture.executor.execute(session(1),
                toolCall("finish_interview", Map.of("summary", "done")), ToolExecutionContext.FIRST_QUESTION);
        assertRejected(result, ToolExecutionResult.ToolRejectionReason.INVALID_FIRST_QUESTION_TOOL);
        verify(fixture.sessionMapper, never()).update(any(), any());
        verify(fixture.questionMapper, never()).insert(any(Question.class));
    }

    private void firstQuestionDoesNotScore() {
        Fixture fixture = new Fixture();
        InterviewSession session = session(2);
        ToolExecutionResult result = fixture.executor.execute(session,
                toolCall("ask_followup", validArguments("FAMILIAR_BUT_SHALLOW")), ToolExecutionContext.FIRST_QUESTION);
        ToolExecutionResult.NextQuestion next = assertInstanceOf(ToolExecutionResult.NextQuestion.class, result);
        assertEquals("下一个问题", next.question());
        assertNull(next.proficiencyLevel());
        assertEquals(2, session.getCurrentRound());
        verify(fixture.assessmentMapper, never()).insert(any(Assessment.class));
    }

    private void invalidProficiencyIsRejected() {
        Fixture fixture = new Fixture();
        ToolExecutionResult result = fixture.executor.execute(session(1),
                toolCall("change_topic", validArguments("FAMILIAR_BUT_SHALLOW")), ToolExecutionContext.ANSWER);
        assertRejected(result, ToolExecutionResult.ToolRejectionReason.INVALID_PROFICIENCY);
        verifyNoToolWrites(fixture);
    }

    private void malformedJsonIsRejected() {
        Fixture fixture = new Fixture();
        ToolExecutionResult result = fixture.executor.execute(session(1), rawToolCall("ask_followup", "not-json"),
                ToolExecutionContext.ANSWER);
        assertRejected(result, ToolExecutionResult.ToolRejectionReason.INVALID_ARGUMENTS_JSON);
        verifyNoToolWrites(fixture);
    }

    private void followupLimitIsEnforced() {
        Fixture fixture = new Fixture();
        when(fixture.chatMapper.selectOne(any())).thenReturn(userAnswer());
        when(fixture.questionMapper.selectOne(any())).thenReturn(currentQuestion());
        when(fixture.questionMapper.selectCount(any())).thenReturn(3L);
        ToolExecutionResult result = fixture.executor.execute(session(1),
                toolCall("ask_followup", validArguments("FAMILIAR_BUT_SHALLOW")), ToolExecutionContext.ANSWER);
        assertRejected(result, ToolExecutionResult.ToolRejectionReason.FOLLOWUP_LIMIT_REACHED);
        verify(fixture.assessmentMapper).insert(any(Assessment.class));
        verify(fixture.questionMapper, never()).insert(any(Question.class));
    }

    private void changeTopicAdvancesRoundOnce() {
        Fixture fixture = new Fixture();
        when(fixture.chatMapper.selectOne(any())).thenReturn(userAnswer());
        when(fixture.questionMapper.selectOne(any())).thenReturn(currentQuestion());
        when(fixture.chatMapper.selectList(any())).thenReturn(List.of());
        when(fixture.sessionMapper.update(any(), any())).thenReturn(1);
        InterviewSession session = session(1);
        ToolExecutionResult result = fixture.executor.execute(session,
                toolCall("change_topic", validArguments("VERY_FAMILIAR")), ToolExecutionContext.ANSWER);
        assertInstanceOf(ToolExecutionResult.NextQuestion.class, result);
        assertEquals(2, session.getCurrentRound());
        verify(fixture.questionMapper).insert(any(Question.class));
    }

    private void finishPersistsTerminalState() {
        Fixture fixture = new Fixture();
        InterviewSession current = session(15);
        InterviewSession persisted = session(15);
        persisted.setStatus("PASSED");
        persisted.setFinalScore(java.math.BigDecimal.TEN);
        when(fixture.sessionMapper.selectById(SESSION_ID)).thenReturn(current, persisted);
        when(fixture.chatMapper.selectList(any())).thenReturn(List.of(evaluatedUserAnswer("VERY_FAMILIAR")));
        when(fixture.sessionMapper.update(any(), any())).thenReturn(1);
        ToolExecutionResult result = fixture.executor.execute(current,
                toolCall("finish_interview", Map.of("summary", "done")), ToolExecutionContext.ANSWER);
        assertInstanceOf(ToolExecutionResult.Finished.class, result);
        verify(fixture.sessionMapper).update(any(), any());
    }

    private void unknownToolIsRejected() {
        Fixture fixture = new Fixture();
        ToolExecutionResult result = fixture.executor.execute(session(1),
                toolCall("invent_tool", validArguments("VERY_FAMILIAR")), ToolExecutionContext.ANSWER);
        assertRejected(result, ToolExecutionResult.ToolRejectionReason.UNKNOWN_TOOL);
        verifyNoToolWrites(fixture);
    }

    private void missingFieldIsRejected() {
        Fixture fixture = new Fixture();
        ToolExecutionResult result = fixture.executor.execute(session(1),
                toolCall("ask_followup", Map.of("question", "q", "proficiency_level", "FAMILIAR_BUT_SHALLOW")),
                ToolExecutionContext.ANSWER);
        assertRejected(result, ToolExecutionResult.ToolRejectionReason.MISSING_REQUIRED_FIELD);
        verifyNoToolWrites(fixture);
    }

    private void wrongFieldTypeIsRejected() {
        Fixture fixture = new Fixture();
        Map<String, Object> arguments = new HashMap<>(validArguments("FAMILIAR_BUT_SHALLOW"));
        arguments.put("question", 123);
        ToolExecutionResult result = fixture.executor.execute(session(1),
                toolCall("ask_followup", arguments), ToolExecutionContext.ANSWER);
        assertRejected(result, ToolExecutionResult.ToolRejectionReason.MISSING_REQUIRED_FIELD);
        verifyNoToolWrites(fixture);
    }

    private void emptyArgumentsAreRejected() {
        Fixture fixture = new Fixture();
        ToolExecutionResult result = fixture.executor.execute(session(1), rawToolCall("ask_followup", "  "),
                ToolExecutionContext.ANSWER);
        assertRejected(result, ToolExecutionResult.ToolRejectionReason.INVALID_ARGUMENTS_JSON);
        verifyNoToolWrites(fixture);
    }

    private void multipleToolCallsAreCorrected() {
        Fixture fixture = new Fixture();
        when(fixture.sessionMapper.selectById(SESSION_ID)).thenReturn(session(1));
        when(fixture.chatMapper.selectOne(any())).thenReturn(userAnswer());
        when(fixture.questionMapper.selectOne(any())).thenReturn(currentQuestion());
        when(fixture.questionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.aiClient.chatWithToolsRetry(any(), any(), any(Integer.class)))
                .thenReturn(response(List.of(toolCall("ask_followup", validArguments("FAMILIAR_BUT_SHALLOW")),
                                toolCall("change_topic", validArguments("VERY_FAMILIAR"))), null),
                        response(List.of(toolCall("ask_followup", validArguments("FAMILIAR_BUT_SHALLOW"))), null));
        when(fixture.chatMapper.selectList(any())).thenReturn(List.of());
        when(fixture.questionMapper.selectCount(any())).thenReturn(0L);
        Map<String, Object> result = fixture.service.answer(SESSION_ID, "answer");
        assertEquals(false, result.get("finished"));
        assertNotNull(result.get("question"));
        verify(fixture.aiClient, org.mockito.Mockito.times(2)).chatWithToolsRetry(any(), any(), any(Integer.class));
        verify(fixture.assessmentMapper).insert(any(Assessment.class));
    }

    private void textOnlyResponseIsCorrected() {
        Fixture fixture = new Fixture();
        when(fixture.sessionMapper.selectById(SESSION_ID)).thenReturn(session(1));
        when(fixture.chatMapper.selectOne(any())).thenReturn(userAnswer());
        when(fixture.questionMapper.selectOne(any())).thenReturn(currentQuestion());
        when(fixture.questionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.aiClient.chatWithToolsRetry(any(), any(), any(Integer.class)))
                .thenReturn(response(List.of(), "直接文本"),
                        response(List.of(toolCall("ask_followup", validArguments("FAMILIAR_BUT_SHALLOW"))), null));
        when(fixture.chatMapper.selectList(any())).thenReturn(List.of());
        when(fixture.questionMapper.selectCount(any())).thenReturn(0L);
        Map<String, Object> result = fixture.service.answer(SESSION_ID, "answer");
        assertEquals(false, result.get("finished"));
        assertNotNull(result.get("question"));
        verify(fixture.aiClient, org.mockito.Mockito.times(2)).chatWithToolsRetry(any(), any(), any(Integer.class));
    }

    private void correctionLimitDegradesWithoutFinishing() {
        Fixture fixture = new Fixture();
        when(fixture.sessionMapper.selectById(SESSION_ID)).thenReturn(session(1));
        when(fixture.aiClient.chatWithToolsRetry(any(), any(), any(Integer.class)))
                .thenReturn(response(List.of(), "文本")).thenReturn(response(List.of(), "文本"))
                .thenReturn(response(List.of(), "文本"));
        when(fixture.chatMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> result = fixture.service.answer(SESSION_ID, "answer");
        assertEquals(false, result.get("finished"));
        assertEquals(1, sessionRoundFromInsertedQuestion(fixture));
        verify(fixture.aiClient, org.mockito.Mockito.times(3)).chatWithToolsRetry(any(), any(), any(Integer.class));
        verify(fixture.assessmentMapper, never()).insert(any(Assessment.class));
        verify(fixture.sessionMapper, never()).update(any(), any());
    }

    private void aiFailureDegradesWithoutFinishing() {
        Fixture fixture = new Fixture();
        when(fixture.sessionMapper.selectById(SESSION_ID)).thenReturn(session(1));
        when(fixture.aiClient.chatWithToolsRetry(any(), any(), any(Integer.class)))
                .thenThrow(new AiServiceException("offline fixture failure"));
        when(fixture.chatMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> result = fixture.service.answer(SESSION_ID, "answer");
        assertEquals(false, result.get("finished"));
        verify(fixture.sessionMapper, never()).update(any(), any());
        verify(fixture.assessmentMapper, never()).insert(any(Assessment.class));
        verify(fixture.questionMapper).insert(any(Question.class));
    }

    private void positionRetrievalIsIsolated(String position, String expectedId) throws Exception {
        RagFixture fixture = new RagFixture(false);
        List<DocumentChunk> results = fixture.ragService.retrieve("query", 3, position);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(chunk -> position.equals(chunk.getPosition())));
        assertEquals(expectedId, results.getFirst().getId());
        verify(fixture.embeddingService).embed("query");
    }

    private void unknownPositionReturnsNoResults() throws Exception {
        RagFixture fixture = new RagFixture(false);
        List<DocumentChunk> results = fixture.ragService.retrieve("query", 3, "unknown");
        assertTrue(results.isEmpty());
        verify(fixture.embeddingService, never()).embed("query");
    }

    private void invalidTopKIsRejected() throws Exception {
        RagFixture fixture = new RagFixture(false);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.ragService.retrieve("query", 0, "java"));
        verify(fixture.embeddingService, never()).embed("query");
    }

    private void recallAtThreeIsCorrect() throws Exception {
        KnowledgeBaseStorage storage = new KnowledgeBaseStorage();
        List<DocumentChunk> chunks = storage.load().stream()
                .filter(chunk -> "java".equals(chunk.getPosition()))
                .toList();
        assertTrue(chunks.size() >= 2, "local knowledge base must contain at least two Java chunks");

        EmbeddingService embeddingService = mock(EmbeddingService.class);
        TypeSafeClient typeSafeClient = mock(TypeSafeClient.class);
        when(typeSafeClient.isAvailable()).thenReturn(false);
        when(embeddingService.embed("真实知识库查询")).thenReturn(chunks.getFirst().getEmbedding());
        RagService ragService = new RagService(embeddingService, storage, typeSafeClient);
        ragService.loadKnowledgeBase();

        List<String> actual = ragService.retrieve("真实知识库查询", 3, "java").stream()
                .map(DocumentChunk::getId).toList();
        List<Set<String>> expected = List.of(Set.of(chunks.getFirst().getId()));
        assertEquals(1.0, RagEvaluationMetrics.recallAt(expected, List.of(actual), 3), 0.00001);
        assertEquals(chunks.getFirst().getId(), actual.getFirst(),
                "gold must be the top chunk produced from the real local knowledge base");
    }

    private void recallAtFiveIsCorrect() {
        List<Set<String>> expected = List.of(Set.of("a", "b"), Set.of("c"));
        List<List<String>> actual = List.of(List.of("a", "x", "b"), List.of("y", "z", "c"));
        assertEquals(1.0, RagEvaluationMetrics.recallAt(expected, actual, 5), 0.00001);
    }

    private void reciprocalRankIsCorrect() {
        List<Set<String>> expected = List.of(Set.of("a"), Set.of("b"), Set.of("c"));
        List<List<String>> actual = List.of(List.of("a", "x"), List.of("x", "b"), List.of("x", "y", "c"));
        assertEquals((1.0 + 0.5 + (1.0 / 3.0)) / 3.0, RagEvaluationMetrics.mrr(expected, actual), 0.00001);
    }

    private void rerankerFailureFallsBackToCosine() throws Exception {
        RagFixture fixture = new RagFixture(true);
        List<DocumentChunk> results = fixture.ragService.retrieve("query", 2, "java");
        assertEquals(List.of("java-1", "java-2"), results.stream().map(DocumentChunk::getId).toList());
        assertTrue(results.getFirst().getScore() > results.getLast().getScore());
        verify(fixture.typeSafeClient).systemOne(any(), any());
    }

    private void embeddingResponseIndexesRestoreInputOrder() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(builder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(String.class))).thenReturn(bodySpec);
        when(bodySpec.header(any(String.class), any(String.class))).thenReturn(bodySpec);
        org.mockito.Mockito.doReturn(bodySpec).when(bodySpec).bodyValue(any());
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(
                "{\"data\":[{\"index\":1,\"embedding\":[2.0]},{\"index\":0,\"embedding\":[1.0]}]}"));
        EmbeddingService service = new EmbeddingService(builder, JSON);
        ReflectionTestUtils.setField(service, "embeddingUrl", "https://offline.invalid");
        ReflectionTestUtils.setField(service, "apiKey", "offline-key");
        ReflectionTestUtils.setField(service, "model", "offline-model");

        List<List<Double>> embeddings = service.embedBatch(List.of("first", "second"));
        assertEquals(List.of(1.0), embeddings.get(0));
        assertEquals(List.of(2.0), embeddings.get(1));
    }

    private void changeTopicSynchronizesInMemoryRound() {
        Fixture fixture = new Fixture();
        when(fixture.chatMapper.selectOne(any())).thenReturn(userAnswer());
        when(fixture.questionMapper.selectOne(any())).thenReturn(currentQuestion());
        when(fixture.chatMapper.selectList(any())).thenReturn(List.of());
        when(fixture.sessionMapper.update(any(), any())).thenReturn(1);
        InterviewSession session = session(1);
        fixture.executor.execute(session,
                toolCall("change_topic", validArguments("VERY_FAMILIAR")), ToolExecutionContext.ANSWER);
        assertEquals(2, session.getCurrentRound());
    }

    private void mybatisMetadataSupportsLambdaExecution() {
        assertNotNull(TableInfoHelper.getTableInfo(InterviewSession.class));
        assertNotNull(TableInfoHelper.getTableInfo(ChatMessage.class));
        assertNotNull(TableInfoHelper.getTableInfo(Question.class));
        Fixture fixture = new Fixture();
        ToolExecutionResult result = fixture.executor.execute(session(1),
                toolCall("ask_followup", validArguments("FAMILIAR_BUT_SHALLOW")), ToolExecutionContext.FIRST_QUESTION);
        assertInstanceOf(ToolExecutionResult.NextQuestion.class, result);
        verify(fixture.questionMapper).insert(any(Question.class));
    }

    private void surefireEnablesMockitoSelfAttach() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertTrue(pom.contains("maven-surefire-plugin"));
        assertTrue(pom.contains("-Djdk.attach.allowAttachSelf=true"));
    }

    private void assessmentsLoadQuestionsInOneBatch() {
        Fixture fixture = new Fixture();
        when(fixture.sessionMapper.selectById(SESSION_ID)).thenReturn(session(1));
        Assessment one = assessment(201L, 301L);
        Assessment two = assessment(202L, 302L);
        when(fixture.assessmentMapper.selectList(any())).thenReturn(List.of(one, two));
        when(fixture.questionMapper.selectBatchIds(List.of(301L, 302L))).thenReturn(List.of(
                question(301L, "q1"), question(302L, "q2")));
        UserContext.setCurrentUserId(USER_ID);
        try {
            List<Map<String, Object>> results = fixture.service.getAssessments(SESSION_ID);
            assertEquals(2, results.size());
            assertEquals("q1", results.get(0).get("questionContent"));
            assertEquals("q2", results.get(1).get("questionContent"));
            verify(fixture.questionMapper).selectBatchIds(List.of(301L, 302L));
            verify(fixture.questionMapper, never()).selectById(any());
        } finally {
            UserContext.clear();
        }
    }

    private void retryUsesLoadedSessionOnce() {
        Fixture fixture = new Fixture();
        InterviewSession old = session(1);
        old.setUserId(USER_ID);
        old.setTargetPosition("backend");
        old.setPosition("java");
        old.setAttemptNumber(2);
        when(fixture.sessionMapper.selectById(SESSION_ID)).thenReturn(old);
        InterviewSession latest = session(1);
        latest.setAttemptNumber(2);
        when(fixture.sessionMapper.selectOne(any())).thenReturn(latest);
        when(fixture.sessionMapper.insert(any(InterviewSession.class))).thenAnswer(invocation -> {
            InterviewSession inserted = invocation.getArgument(0);
            inserted.setId(202L);
            return 1;
        });
        UserContext.setCurrentUserId(USER_ID);
        try {
            InterviewSession retried = fixture.service.prepareRetry(SESSION_ID);
            assertEquals(3, retried.getAttemptNumber());
            assertEquals(SESSION_ID, retried.getPreviousSessionId());
            verify(fixture.sessionMapper).selectById(SESSION_ID);
        } finally {
            UserContext.clear();
        }
    }

    private void protocolFailureDoesNotSettleSession() {
        Fixture fixture = new Fixture();
        InterviewSession current = session(1);
        when(fixture.sessionMapper.selectById(SESSION_ID)).thenReturn(current);
        when(fixture.aiClient.chatWithToolsRetry(any(), any(), any(Integer.class)))
                .thenReturn(response(List.of(), "text")).thenReturn(response(List.of(), "text"))
                .thenReturn(response(List.of(), "text"));
        when(fixture.chatMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> result = fixture.service.answer(SESSION_ID, "answer");
        assertEquals(false, result.get("finished"));
        assertEquals("IN_PROGRESS", current.getStatus());
        verify(fixture.sessionMapper, never()).update(any(), any());
        verify(fixture.assessmentMapper, never()).insert(any(Assessment.class));
    }

    private static int sessionRoundFromInsertedQuestion(Fixture fixture) {
        org.mockito.ArgumentCaptor<Question> captor = org.mockito.ArgumentCaptor.forClass(Question.class);
        verify(fixture.questionMapper).insert(captor.capture());
        return captor.getValue().getRoundNum();
    }

    private static void verifyNoToolWrites(Fixture fixture) {
        verify(fixture.sessionMapper, never()).update(any(), any());
        verify(fixture.questionMapper, never()).insert(any(Question.class));
        verify(fixture.assessmentMapper, never()).insert(any(Assessment.class));
        verify(fixture.chatMapper, never()).insert(any(ChatMessage.class));
    }

    private static void assertRejected(ToolExecutionResult result,
                                       ToolExecutionResult.ToolRejectionReason expectedReason) {
        ToolExecutionResult.Rejected rejected = assertInstanceOf(ToolExecutionResult.Rejected.class, result);
        assertEquals(expectedReason, rejected.reason());
    }

    private static Map<String, Object> validArguments(String proficiency) {
        return Map.of("question", "下一个问题", "reason", "回答评价", "proficiency_level", proficiency);
    }

    private static ChatResponse.ToolCall toolCall(String name, Map<String, Object> arguments) {
        try {
            return rawToolCall(name, JSON.writeValueAsString(arguments));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static ChatResponse.ToolCall rawToolCall(String name, String arguments) {
        ChatResponse.FunctionCall function = new ChatResponse.FunctionCall();
        function.setName(name);
        function.setArguments(arguments);
        ChatResponse.ToolCall call = new ChatResponse.ToolCall();
        call.setFunction(function);
        return call;
    }

    private static ChatResponse response(List<ChatResponse.ToolCall> calls, String content) {
        ChatResponse.AssistantMessage message = new ChatResponse.AssistantMessage();
        message.setRole("assistant");
        message.setToolCalls(calls);
        message.setContent(content);
        ChatResponse.Choice choice = new ChatResponse.Choice();
        choice.setMessage(message);
        ChatResponse response = new ChatResponse();
        response.setChoices(List.of(choice));
        return response;
    }

    private static InterviewSession session(int round) {
        InterviewSession session = new InterviewSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setCurrentRound(round);
        session.setInterviewStage(1);
        session.setStatus("IN_PROGRESS");
        session.setTargetPosition("backend");
        session.setPosition("java");
        session.setResumeText("");
        return session;
    }

    private static ChatMessage userAnswer() {
        ChatMessage message = new ChatMessage();
        message.setId(11L);
        message.setSessionId(SESSION_ID);
        message.setRole("user");
        message.setContent("answer");
        return message;
    }

    private static ChatMessage evaluatedUserAnswer(String proficiency) {
        ChatMessage message = userAnswer();
        message.setProficiencyLevel(proficiency);
        return message;
    }

    private static Question currentQuestion() {
        return question(21L, "current question");
    }

    private static Question question(long id, String content) {
        Question question = new Question();
        question.setId(id);
        question.setSessionId(SESSION_ID);
        question.setContent(content);
        question.setRoundNum(1);
        return question;
    }

    private static Assessment assessment(long id, long questionId) {
        Assessment assessment = new Assessment();
        assessment.setId(id);
        assessment.setSessionId(SESSION_ID);
        assessment.setQuestionId(questionId);
        assessment.setScore(java.math.BigDecimal.valueOf(6));
        return assessment;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? "(no message)" : message.replace("\n", " ").replace("|", "\\|");
    }

    private static String renderReport() {
        long passed = RESULTS.stream().filter(CaseResult::passed).count();
        double rate = RESULTS.isEmpty() ? 0 : 100.0 * passed / CASES.size();
        StringBuilder report = new StringBuilder("# Offline Evaluation Report\n\n");
        report.append("- Executed: ").append(RESULTS.size()).append(" / ").append(CASES.size()).append("\n");
        report.append("- Passed: ").append(passed).append("\n");
        report.append("- Failed: ").append(RESULTS.size() - passed).append("\n");
        report.append("- Pass rate: ").append(String.format(java.util.Locale.ROOT, "%.1f%%", rate)).append("\n");
        report.append("- Mode: offline, external AI/Embedding/TypeSafe calls mocked\n\n");
        report.append("## Case Results\n\n| ID | Description | Entry point | Result | Details |\n");
        report.append("|---|---|---|---|---|\n");
        for (Case evaluationCase : CASES) {
            CaseResult result = RESULTS.stream().filter(r -> r.id().equals(evaluationCase.id()))
                    .findFirst().orElse(null);
            String status = result == null ? "NOT RUN" : result.passed() ? "PASS" : "FAIL";
            String details = result == null ? "No execution result" : result.passed() ? "Assertions passed"
                    : result.failureType() + ": " + result.details();
            report.append("| ").append(evaluationCase.id()).append(" | ").append(evaluationCase.description())
                    .append(" | ").append(evaluationCase.entryPoint()).append(" | ").append(status)
                    .append(" | ").append(details).append(" |\n");
        }
        report.append("\n## Failure Details\n\n");
        List<CaseResult> failures = RESULTS.stream().filter(result -> !result.passed()).toList();
        if (failures.isEmpty()) {
            report.append("None.\n");
        } else {
            for (CaseResult failure : failures) {
                report.append("- `").append(failure.id()).append("`: ").append(failure.failureType())
                        .append(" - ").append(failure.details()).append("\n");
            }
        }
        report.append("\nGenerated by `OfflineEvaluationCasesTest`; run `mvn -q -Dtest=OfflineEvaluationCasesTest test`.\n");
        return report.toString();
    }

    private record Case(String id, String description, String entryPoint) {
    }

    private record CaseResult(String id, String description, boolean passed, String failureType, String details) {
    }

    private static final class Fixture {
        final InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        final ChatMessageMapper chatMapper = mock(ChatMessageMapper.class);
        final QuestionMapper questionMapper = mock(QuestionMapper.class);
        final AssessmentMapper assessmentMapper = mock(AssessmentMapper.class);
        final AiClient aiClient = mock(AiClient.class);
        final PromptLoader promptLoader = mock(PromptLoader.class);
        final RagService ragService = mock(RagService.class);
        final ToolExecutor executor = new ToolExecutor(sessionMapper, chatMapper, questionMapper,
                assessmentMapper, JSON);
        final InterviewServiceImpl service = new InterviewServiceImpl(sessionMapper, chatMapper, questionMapper,
                assessmentMapper, aiClient, promptLoader, ragService, executor);

        Fixture() {
            when(promptLoader.getPrompt(any(Integer.class), any(String.class))).thenReturn("offline prompt");
            try {
                when(ragService.retrieve(any(String.class), any(Integer.class), any(String.class)))
                        .thenReturn(List.of());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            when(ragService.buildContext(any())).thenReturn("");
        }
    }

    private static final class RagFixture {
        final EmbeddingService embeddingService = mock(EmbeddingService.class);
        final KnowledgeBaseStorage storage = mock(KnowledgeBaseStorage.class);
        final TypeSafeClient typeSafeClient = mock(TypeSafeClient.class);
        final RagService ragService = new RagService(embeddingService, storage, typeSafeClient);

        RagFixture(boolean jevAvailable) throws Exception {
            when(embeddingService.embed("query")).thenReturn(List.of(1.0, 0.0));
            when(typeSafeClient.isAvailable()).thenReturn(jevAvailable);
            if (jevAvailable) {
                when(typeSafeClient.systemOne(any(), any())).thenThrow(new IllegalStateException("offline Jev failure"));
            }
            when(storage.load()).thenReturn(List.of(
                    new DocumentChunk("java-1", "Java knowledge", List.of(1.0, 0.0), 0, "java.md", "java"),
                    new DocumentChunk("java-2", "Java secondary", List.of(0.8, 0.2), 0, "java.md", "java"),
                    new DocumentChunk("ai-1", "AI knowledge", List.of(1.0, 0.0), 0, "ai.md", "ai")));
            ragService.loadKnowledgeBase();
        }
    }
}
