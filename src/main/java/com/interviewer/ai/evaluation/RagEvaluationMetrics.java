package com.interviewer.ai.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline retrieval metrics. Ranks are zero-based document ids in result order.
 */
public final class RagEvaluationMetrics {

    private RagEvaluationMetrics() {
    }

    public static double recallAt(List<Set<String>> expected, List<List<String>> actual, int k) {
        validate(expected, actual, k);
        if (expected.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < expected.size(); i++) {
            Set<String> relevant = expected.get(i);
            if (relevant.isEmpty()) {
                continue;
            }
            Set<String> retrieved = new HashSet<>(actual.get(i).subList(0, Math.min(k, actual.get(i).size())));
            Set<String> hits = new HashSet<>(retrieved);
            hits.retainAll(relevant);
            total += (double) hits.size() / relevant.size();
        }
        return total / expected.size();
    }

    public static double mrr(List<Set<String>> expected, List<List<String>> actual) {
        validate(expected, actual, 1);
        if (expected.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < expected.size(); i++) {
            Set<String> relevant = expected.get(i);
            List<String> ranking = actual.get(i);
            for (int rank = 0; rank < ranking.size(); rank++) {
                if (relevant.contains(ranking.get(rank))) {
                    total += 1.0 / (rank + 1);
                    break;
                }
            }
        }
        return total / expected.size();
    }

    private static void validate(List<Set<String>> expected, List<List<String>> actual, int k) {
        if (expected == null || actual == null || expected.size() != actual.size()) {
            throw new IllegalArgumentException("评测期望和实际结果数量不一致");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k 必须大于 0");
        }
    }
}
