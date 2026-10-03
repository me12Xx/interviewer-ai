package com.interviewer.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KnowledgeBaseStorage {
    
    private static final String STORAGE_FILE = "data/knowledge_base.json";
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public void save(List<DocumentChunk> chunks) throws IOException {
        File file = new File(STORAGE_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, chunks);
        log.info("知识库已保存到 {}, 共 {} 个文档块", STORAGE_FILE, chunks.size());
    }
    
    public List<DocumentChunk> load() throws IOException {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) {
            log.warn("知识库文件不存在: {}", STORAGE_FILE);
            return new ArrayList<>();
        }
        List<DocumentChunk> chunks = objectMapper.readValue(file, new TypeReference<List<DocumentChunk>>() {});
        log.info("知识库已加载，共 {} 个文档块", chunks.size());
        return chunks;
    }
    
    public boolean exists() {
        return new File(STORAGE_FILE).exists();
    }
}
