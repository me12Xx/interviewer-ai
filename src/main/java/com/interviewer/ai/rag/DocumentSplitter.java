package com.interviewer.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档切分服务
 * 策略：按段落切分，长段落按句子拆分，每块 300-500 字
 */
@Component
public class DocumentSplitter {

    private static final Logger log = LoggerFactory.getLogger(DocumentSplitter.class);
    
    private static final int MIN_CHUNK_SIZE = 50;
    private static final int MAX_CHUNK_SIZE = 500;
    
    /**
     * 切分文档
     * @param document 原始文档（Markdown 格式）
     * @return 切分后的文本块
     */
    public List<String> split(String document) {
        log.debug("=== DocumentSplitter DEBUG ===");
        log.debug("原始文档长度: {}", document.length());
        
        // 1. 按段落切分（\n\n 或 \r\n\r\n 分隔，兼容 Windows 和 Unix）
        String normalizedDoc = document.replace("\r\n", "\n");
        log.debug("归一化后文档长度: {}", normalizedDoc.length());
        log.debug("前100字符: {}", normalizedDoc.substring(0, Math.min(100, normalizedDoc.length())));
        
        String[] rawParagraphs = normalizedDoc.split("\n\n");
        log.debug("split() 返回段落数量: {}", rawParagraphs.length);
        
        List<String> paragraphs = Arrays.stream(rawParagraphs)
            .map(String::trim)
            .filter(s -> s.length() > MIN_CHUNK_SIZE)  // 过滤过短段落
            .collect(Collectors.toList());
        
        log.debug("过滤后段落数量: {}", paragraphs.size());
        for (int i = 0; i < paragraphs.size(); i++) {
            log.debug("段落{}长度: {}", i, paragraphs.get(i).length());
        }
        
        // 2. 处理长段落
        List<String> chunks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= MAX_CHUNK_SIZE) {
                chunks.add(paragraph);
            } else {
                // 长段落按句子拆分
                chunks.addAll(splitLongParagraph(paragraph));
            }
        }
        
        log.debug("最终切分块数量: {}", chunks.size());
        log.debug("=========================");
        
        return chunks;
    }
    
    /**
     * 拆分长段落（按句子）
     */
    private List<String> splitLongParagraph(String paragraph) {
        List<String> result = new ArrayList<>();
        String[] sentences = paragraph.split("[。！？]");
        
        StringBuilder buffer = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.trim().isEmpty()) {
                continue;
            }
            
            // 如果加上当前句子超过最大长度，保存当前 buffer
            if (buffer.length() + sentence.length() > MAX_CHUNK_SIZE && buffer.length() > 0) {
                result.add(buffer.toString().trim());
                buffer = new StringBuilder(sentence).append("。");
            } else {
                buffer.append(sentence).append("。");
            }
        }
        
        // 保存最后一个 buffer
        if (buffer.length() > 0) {
            result.add(buffer.toString().trim());
        }
        
        return result;
    }
}
