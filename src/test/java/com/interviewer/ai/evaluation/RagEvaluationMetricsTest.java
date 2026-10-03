package com.interviewer.ai.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagEvaluationMetricsTest {

    @Test
    void calculatesRecallAt3RecallAt5AndMrr() {
        List<Set<String>> expected = List.of(
                Set.of("a", "b"),
                Set.of("d"),
                Set.of("g", "h"));
        List<List<String>> actual = List.of(
                List.of("x", "a", "b", "z", "q"),
                List.of("x", "y", "z", "d", "q"),
                List.of("h", "x", "g", "q", "r"));

        assertEquals(2.0 / 3.0, RagEvaluationMetrics.recallAt(expected, actual, 3), 0.0001);
        assertEquals(1.0, RagEvaluationMetrics.recallAt(expected, actual, 5), 0.0001);
        assertEquals((0.5 + 0.25 + 1.0) / 3, RagEvaluationMetrics.mrr(expected, actual), 0.0001);
    }
}
