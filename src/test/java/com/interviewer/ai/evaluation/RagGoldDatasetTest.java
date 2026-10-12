package com.interviewer.ai.evaluation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RagGoldDatasetTest {
    @Test
    void containsThirtyCasesWithExistingManuallyReviewedChunkIds() throws Exception {
        RagGoldDataset.Dataset dataset = RagGoldDataset.load();

        assertEquals(30, dataset.cases().size());
        assertEquals(1, dataset.version());
        assertEquals("embedding-3", dataset.knowledge_base().embedding_model());

        Set<String> availableIds = RagGoldCorpus.load().ids();
        Set<String> caseIds = new HashSet<>();
        for (RagGoldDataset.Case gold : dataset.cases()) {
            assertTrue(caseIds.add(gold.id()), "duplicate gold case: " + gold.id());
            assertFalse(gold.query().isBlank(), gold.id());
            assertFalse(gold.relevant_chunk_ids().isEmpty(), gold.id());
            for (String chunkId : gold.relevant_chunk_ids()) {
                assertTrue(availableIds.contains(chunkId),
                        gold.id() + " references missing chunk " + chunkId);
            }
        }
    }

}
