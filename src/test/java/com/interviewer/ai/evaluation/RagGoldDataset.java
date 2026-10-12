package com.interviewer.ai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

final class RagGoldDataset {
    private RagGoldDataset() {
    }

    static Dataset load() throws IOException {
        try (InputStream input = RagGoldDataset.class.getResourceAsStream("/evaluation/rag_gold_30.json")) {
            if (input == null) {
                throw new IOException("Missing /evaluation/rag_gold_30.json");
            }
            return new ObjectMapper().readValue(input, Dataset.class);
        }
    }

    record Dataset(int version, String annotation, KnowledgeBase knowledge_base, List<Case> cases) {
    }

    record KnowledgeBase(List<String> positions, String id_scheme, String splitter, String embedding_model) {
    }

    record Case(String id, String query, String position, List<String> relevant_chunk_ids, String rationale) {
    }
}
