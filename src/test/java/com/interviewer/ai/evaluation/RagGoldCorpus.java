package com.interviewer.ai.evaluation;

import com.interviewer.ai.rag.DocumentChunk;
import com.interviewer.ai.rag.DocumentSplitter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RagGoldCorpus {
    private static final List<String> JAVA_SOURCES = List.of(
            "Spring框架.md", "Redis.md", "MySQL索引.md", "MySQL事务.md",
            "JVM内存与GC.md", "Java集合源码.md", "Java集合框架.md",
            "Java并发进阶.md", "Java并发基础.md");

    private final Map<String, String> stableIdsByContent;

    private RagGoldCorpus(Map<String, String> stableIdsByContent) {
        this.stableIdsByContent = stableIdsByContent;
    }

    static RagGoldCorpus load() throws IOException {
        DocumentSplitter splitter = new DocumentSplitter();
        Map<String, String> ids = new HashMap<>();
        for (String source : JAVA_SOURCES) {
            String text;
            try (InputStream input = RagGoldCorpus.class.getResourceAsStream("/knowledge/java/" + source)) {
                if (input == null) {
                    throw new IOException("Missing knowledge source " + source);
                }
                text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            List<String> chunks = splitter.split(text);
            for (int index = 0; index < chunks.size(); index++) {
                String id = source.replaceFirst("\\.md$", "") + "_chunk_" + index;
                ids.put(key(source, chunks.get(index)), id);
            }
        }
        return new RagGoldCorpus(ids);
    }

    String stableId(DocumentChunk chunk) {
        String id = stableIdsByContent.get(key(chunk.getSource(), chunk.getText()));
        if (id == null) {
            throw new IllegalStateException("Stored chunk does not match current corpus: " + chunk.getId());
        }
        return id;
    }

    java.util.Set<String> ids() {
        return java.util.Set.copyOf(stableIdsByContent.values());
    }

    private static String key(String source, String text) {
        return source + "\u0000" + text;
    }
}
