package com.interviewer.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseInitializer implements CommandLineRunner {
    
    /** 单次 Embedding 请求的最大文本条数，避免请求体过大被服务端拒绝 */
    private static final int EMBED_BATCH_SIZE = 16;
    
    private final DocumentSplitter splitter;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseStorage storage;
    private final RagService ragService;
    
    @Override
    public void run(String... args) throws Exception {
        if (storage.exists()) {
            log.info("知识库文件已存在，跳过构建，直接加载");
            ragService.loadKnowledgeBase();
            return;
        }
        
        log.info("知识库文件不存在，开始构建知识库...");
        buildKnowledgeBase();
        ragService.loadKnowledgeBase();
    }
    
    private void buildKnowledgeBase() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<DocumentChunk> allChunks = new ArrayList<>();
        int resourceCount = 0;

        for (String position : List.of("java", "ai")) {
            Resource[] resources = resolver.getResources("classpath:knowledge/" + position + "/*.md");
            Arrays.sort(resources, java.util.Comparator.comparing(resource -> {
                String filename = resource.getFilename();
                return filename == null ? "" : filename;
            }));
            resourceCount += resources.length;
            log.info("岗位 {} 资源数量: {}", position, resources.length);

            for (int resourceIndex = 0; resourceIndex < resources.length; resourceIndex++) {
                Resource resource = resources[resourceIndex];

                String filename = resource.getFilename();
                if (filename == null || filename.isBlank()) {
                    filename = position + "_resource_" + resourceIndex + ".md";
                    log.warn("资源文件名为空，使用兜底文件名: {}", filename);
                }

                byte[] bytes = resource.getInputStream().readAllBytes();
                String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                log.info("读取文档: {}, 原始字节数: {}, 字符数: {}", filename, bytes.length, content.length());

                List<String> texts = splitter.split(content);
                log.info("文档 {} 切分为 {} 个块", filename, texts.size());

                // 分批向量化：单个文档的所有块一次请求完成，避免逐条串行调用
                for (int from = 0; from < texts.size(); from += EMBED_BATCH_SIZE) {
                    int to = Math.min(from + EMBED_BATCH_SIZE, texts.size());
                    List<String> batch = texts.subList(from, to);

                    List<List<Double>> embeddings = embeddingService.embedBatch(batch);

                    for (int i = 0; i < batch.size(); i++) {
                        DocumentChunk chunk = new DocumentChunk();
                         chunk.setId(generateId(filename, from + i));
                        chunk.setText(batch.get(i));
                        chunk.setEmbedding(embeddings.get(i));
                        chunk.setSource(filename);
                        chunk.setPosition(position);
                        chunk.setScore(0.0);
                        allChunks.add(chunk);
                    }
                    log.info("文档 {} 已向量化 {}/{} 块", filename, to, texts.size());
                }
            }
        }

        log.info("资源总数量: {}", resourceCount);

        if (resourceCount == 0) {
            log.warn("未找到知识库文档，跳过构建");
            return;
        }
        
        storage.save(allChunks);
        log.info("知识库构建完成，共 {} 个文档块", allChunks.size());
    }
    
    private String generateId(String source, int index) {
        return source.replaceFirst("\\.md$", "") + "_chunk_" + index;
    }
}
