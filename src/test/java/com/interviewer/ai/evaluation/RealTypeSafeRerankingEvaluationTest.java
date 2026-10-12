package com.interviewer.ai.evaluation;

import com.interviewer.ai.rag.DocumentChunk;
import com.interviewer.ai.rag.KnowledgeBaseStorage;
import com.interviewer.ai.rag.RagService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "typesafe.enabled=true",
        "spring.main.web-application-type=none"
})
@Tag("external-api")
class RealTypeSafeRerankingEvaluationTest {
    @Autowired
    private RagService ragService;

    @Autowired
    private KnowledgeBaseStorage storage;

    @Test
    void reportsRerankingQualitySeparatelyWithTheConfiguredRealTypeSafeKey() throws Exception {
        assertTrue(System.getenv("TYPESAFE_API_KEY") != null
                        || System.getProperty("typesafe.api-key") != null,
                "TYPESAFE_API_KEY or -Dtypesafe.api-key is required for the isolated TypeSafe evaluation");
        assertFalse(storage.load().isEmpty(), "data/knowledge_base.json must be built before this evaluation");

        RagGoldDataset.Dataset gold = RagGoldDataset.load();
        RagGoldCorpus corpus = RagGoldCorpus.load();
        List<Set<String>> expected = gold.cases().stream()
                .map(testCase -> (Set<String>) new HashSet<>(testCase.relevant_chunk_ids()))
                .toList();
        List<List<String>> reranked = new ArrayList<>();
        for (RagGoldDataset.Case testCase : gold.cases()) {
            List<DocumentChunk> results = ragService.retrieve(testCase.query(), 5, testCase.position());
            reranked.add(results.stream().map(corpus::stableId).toList());
        }

        double recallAt3 = RagEvaluationMetrics.recallAt(expected, reranked, 3);
        double recallAt5 = RagEvaluationMetrics.recallAt(expected, reranked, 5);
        double mrr = RagEvaluationMetrics.mrr(expected, reranked);
        String report = "# Real TypeSafe Reranking Evaluation\n\n"
                + "Gold cases: " + gold.cases().size() + "\n"
                + "TypeSafe model: jev-latest\n"
                + "Embedding baseline: cosine candidates, topK*3\n\n"
                + "| Metric | Value |\n|---|---:|\n"
                + String.format(Locale.ROOT, "| Recall@3 | %.6f |\n| Recall@5 | %.6f |\n| MRR | %.6f |\n", recallAt3, recallAt5, mrr);
        Files.createDirectories(Path.of("target", "evaluation"));
        Files.writeString(Path.of("target", "evaluation", "rag-typesafe-gold-report.md"), report);
        System.out.printf("REAL_TYPESAFE gold=%d recall@3=%.6f recall@5=%.6f mrr=%.6f%n",
                gold.cases().size(), recallAt3, recallAt5, mrr);
        assertEquals(30, gold.cases().size());
    }
}
