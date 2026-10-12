package com.interviewer.ai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewer.ai.rag.DocumentChunk;
import com.interviewer.ai.rag.KnowledgeBaseStorage;
import com.interviewer.ai.rag.RagService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "typesafe.enabled=false")
@TestPropertySource(properties = "spring.main.web-application-type=none")
@Tag("external-api")
class RealAiCosineRagEvaluationTest {
    @Autowired
    private RagService ragService;

    @Autowired
    private KnowledgeBaseStorage storage;

    @Test
    void reportsAiRecallAt3RecallAt5AndMrr() throws Exception {
        GoldSet gold = GoldSet.load();
        assertEquals(30, gold.cases().size());

        List<DocumentChunk> indexed = storage.load();
        Set<String> aiIds = indexed.stream()
                .filter(chunk -> "ai".equals(chunk.getPosition()))
                .map(DocumentChunk::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(31, aiIds.size(), "AI index should contain the 30 topics plus the document metadata chunk");
        assertTrue(aiIds.contains("AI面试知识库_chunk_0"));

        List<Set<String>> expected = new ArrayList<>();
        List<List<String>> actual = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Case testCase : gold.cases()) {
            Set<String> relevant = Set.copyOf(testCase.relevant_chunk_ids());
            List<String> result = ragService.retrieve(testCase.query(), 5, "ai")
                    .stream().map(DocumentChunk::getId).toList();
            expected.add(relevant);
            actual.add(result);
            if (result.stream().noneMatch(relevant::contains)) {
                failures.add(testCase.id() + " -> " + result);
            }
        }

        double recallAt3 = RagEvaluationMetrics.recallAt(expected, actual, 3);
        double recallAt5 = RagEvaluationMetrics.recallAt(expected, actual, 5);
        double mrr = RagEvaluationMetrics.mrr(expected, actual);
        String report = "# Real AI Cosine RAG Evaluation\n\n"
                + "Gold cases: " + gold.cases().size() + "\n"
                + "AI chunks: " + aiIds.size() + " (metadata chunk excluded from gold)\n"
                + "Embedding: embedding-3 via EmbeddingService\n"
                + "TypeSafe: disabled\n\n"
                + "| Metric | Value |\n|---|---:|\n"
                + String.format(java.util.Locale.ROOT,
                "| Recall@3 | %.6f |\n| Recall@5 | %.6f |\n| MRR | %.6f |\n",
                recallAt3, recallAt5, mrr)
                + "\n## Queries without a relevant hit in Top 5\n\n"
                + (failures.isEmpty() ? "None\n" : String.join("\n", failures) + "\n");
        Files.createDirectories(Path.of("target", "evaluation"));
        Files.writeString(Path.of("target", "evaluation", "rag-ai-cosine-gold-report.md"), report);
        System.out.printf("REAL_AI_COSINE gold=%d recall@3=%.6f recall@5=%.6f mrr=%.6f failures=%d%n",
                gold.cases().size(), recallAt3, recallAt5, mrr, failures.size());
    }

    @Test
    void aiAndJavaPositionFiltersDoNotCrossContaminate() throws Exception {
        List<DocumentChunk> aiResults = ragService.retrieve("Transformer 和 RAG 的知识", 10, "ai");
        List<DocumentChunk> javaResults = ragService.retrieve("Java 并发和 JVM 的知识", 10, "java");
        assertFalse(aiResults.isEmpty());
        assertFalse(javaResults.isEmpty());
        assertTrue(aiResults.stream().allMatch(chunk -> "ai".equals(chunk.getPosition())));
        assertTrue(javaResults.stream().allMatch(chunk -> "java".equals(chunk.getPosition())));
    }

    private record GoldSet(int version, String annotation, KnowledgeBase knowledge_base, List<Case> cases) {
        static GoldSet load() throws Exception {
            try (var input = RealAiCosineRagEvaluationTest.class.getResourceAsStream("/evaluation/rag_gold_ai_30.json")) {
                assertNotNull(input, "Missing /evaluation/rag_gold_ai_30.json");
                return new ObjectMapper().readValue(input, GoldSet.class);
            }
        }
    }

    private record KnowledgeBase(List<String> positions, String id_scheme, String splitter, String embedding_model) {
    }

    private record Case(String id, String query, String position, List<String> relevant_chunk_ids, String rationale) {
    }
}
