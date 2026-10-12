package com.interviewer.ai.evaluation;

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
class RealCosineRagEvaluationTest {
    @Autowired
    private RagService ragService;

    @Autowired
    private KnowledgeBaseStorage storage;

    @Test
    void reportsRecallAt3RecallAt5AndMrrOnTheFullGoldSet() throws Exception {
        RagGoldDataset.Dataset gold = RagGoldDataset.load();
        RagGoldCorpus corpus = RagGoldCorpus.load();
        List<Set<String>> expected = new ArrayList<>();
        List<List<String>> actual = new ArrayList<>();
        assertFalse(storage.load().isEmpty(), "data/knowledge_base.json must be built before this real evaluation");

        for (RagGoldDataset.Case testCase : gold.cases()) {
            expected.add(new HashSet<>(testCase.relevant_chunk_ids()));
            List<DocumentChunk> results = ragService.retrieve(testCase.query(), 5, testCase.position());
            actual.add(results.stream().map(corpus::stableId).toList());
        }

        double recallAt3 = RagEvaluationMetrics.recallAt(expected, actual, 3);
        double recallAt5 = RagEvaluationMetrics.recallAt(expected, actual, 5);
        double mrr = RagEvaluationMetrics.mrr(expected, actual);
        String report = "# Real Cosine RAG Evaluation\n\n"
                + "Gold cases: " + gold.cases().size() + "\n"
                + "Embedding: embedding-3 via EmbeddingService\n"
                + "TypeSafe: disabled\n\n"
                + "| Metric | Value |\n|---|---:|\n"
                + String.format(java.util.Locale.ROOT, "| Recall@3 | %.6f |\n| Recall@5 | %.6f |\n| MRR | %.6f |\n", recallAt3, recallAt5, mrr);
        Files.createDirectories(Path.of("target", "evaluation"));
        Files.writeString(Path.of("target", "evaluation", "rag-cosine-gold-report.md"), report);
        System.out.printf("REAL_COSINE gold=%d recall@3=%.6f recall@5=%.6f mrr=%.6f%n",
                gold.cases().size(), recallAt3, recallAt5, mrr);
        assertEquals(gold.cases().size(), expected.size());
    }
}
