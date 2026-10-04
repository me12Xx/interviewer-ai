package com.interviewer.ai.rag;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RAG 重排序 A/B 对比测试
 * 
 * 测试目标：
 * 1. 对比启用/禁用 Jev 重排序的检索结果
 * 2. 评估重排序对检索质量的提升
 * 3. 统计延迟和成本
 * 
 * 运行方式：
 * - Test A（基线）：不启用 Jev，使用原始余弦相似度
 * - Test B（实验组）：启用 Jev 重排序
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Tag("external-api")
class RagRerankerABTest {
    
    @Autowired
    private RagService ragService;
    
    @Autowired
    private KnowledgeBaseStorage storage;
    
    /**
     * 测试查询集合（覆盖不同技术栈）
     */
    private static final String[] TEST_QUERIES = {
        "Spring 如何解决循环依赖",
        "HashMap 的底层实现原理",
        "Redis 持久化机制 RDB 和 AOF 的区别",
        "MySQL 索引失效的场景有哪些",
        "线程池的核心参数和拒绝策略",
        "JVM 垃圾回收算法有哪些",
        "分布式事务的解决方案",
        "微服务如何实现服务熔断和降级"
    };
    
    @BeforeEach
    void setUp() throws Exception {
        // 确保知识库已加载
        if (storage.load().isEmpty()) {
            log.warn("知识库为空，请先构建知识库");
        }
        ragService.loadKnowledgeBase();
    }
    
    /**
     * Test A：基线测试（禁用 Jev）
     * 
     * 环境变量：TYPESAFE_ENABLED=false
     */
    @Test
    void testBaselineWithoutJev() throws Exception {
        log.info("========== Test A: 基线测试（余弦相似度） ==========");
        
        List<TestResult> results = new ArrayList<>();
        
        for (String query : TEST_QUERIES) {
            long startTime = System.currentTimeMillis();
            
            // 执行检索（Top 10）
            List<DocumentChunk> chunks = ragService.retrieve(query, 10, "java");
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            // 记录结果
            TestResult result = new TestResult();
            result.setQuery(query);
            result.setRetrievedCount(chunks.size());
            result.setElapsedMs(elapsed);
            result.setTopChunks(chunks.subList(0, Math.min(3, chunks.size())));
            
            results.add(result);
            
            // 打印前 3 个结果
            log.info("\n查询: {}", query);
            log.info("耗时: {}ms", elapsed);
            for (int i = 0; i < Math.min(3, chunks.size()); i++) {
                DocumentChunk chunk = chunks.get(i);
                log.info("[{}] 分数: {} | 来源: {} | 内容: {}", 
                    i + 1, 
                    formatDecimal(chunk.getScore(), 4),
                    chunk.getSource(),
                    truncate(chunk.getText(), 80));
            }
        }
        
        // 统计汇总
        printSummary("基线测试", results);
    }
    
    /**
     * Test B：实验组测试（启用 Jev 重排序）
     * 
     * 环境变量：
     * - TYPESAFE_API_KEY=你的密钥
     * - TYPESAFE_ENABLED=true
     */
    @Test
    void testWithJevReranking() throws Exception {
        log.info("========== Test B: 实验组（Jev 重排序） ==========");
        
        List<TestResult> results = new ArrayList<>();
        
        for (String query : TEST_QUERIES) {
            long startTime = System.currentTimeMillis();
            
            // 执行检索（Top 10，内部会调用 Jev 重排序）
            List<DocumentChunk> chunks = ragService.retrieve(query, 10, "java");
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            // 记录结果
            TestResult result = new TestResult();
            result.setQuery(query);
            result.setRetrievedCount(chunks.size());
            result.setElapsedMs(elapsed);
            result.setTopChunks(chunks.subList(0, Math.min(3, chunks.size())));
            
            results.add(result);
            
            // 打印前 3 个结果
            log.info("\n查询: {}", query);
            log.info("耗时: {}ms", elapsed);
            for (int i = 0; i < Math.min(3, chunks.size()); i++) {
                DocumentChunk chunk = chunks.get(i);
                log.info("[{}] 分数: {} | 来源: {} | 内容: {}", 
                    i + 1, 
                    formatDecimal(chunk.getScore(), 4),
                    chunk.getSource(),
                    truncate(chunk.getText(), 80));
            }
        }
        
        // 统计汇总
        printSummary("Jev 重排序", results);
    }
    
    /**
     * 打印统计汇总
     */
    private void printSummary(String testName, List<TestResult> results) {
        double avgElapsed = results.stream()
            .mapToLong(TestResult::getElapsedMs)
            .average()
            .orElse(0.0);
        
        long minElapsed = results.stream()
            .mapToLong(TestResult::getElapsedMs)
            .min()
            .orElse(0);
        
        long maxElapsed = results.stream()
            .mapToLong(TestResult::getElapsedMs)
            .max()
            .orElse(0);
        
        log.info("\n========== {} 统计汇总 ==========", testName);
        log.info("测试查询数: {}", results.size());
        log.info("平均耗时: {}ms", formatDecimal(avgElapsed, 2));
        log.info("最小耗时: {}ms", minElapsed);
        log.info("最大耗时: {}ms", maxElapsed);
        log.info("==========================================\n");
    }
    
    /**
     * 截断文本（用于日志展示）
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > maxLength 
            ? text.substring(0, maxLength) + "..." 
            : text;
    }

    private String formatDecimal(double value, int precision) {
        return String.format(Locale.ROOT, "%." + precision + "f", value);
    }
    
    /**
     * 测试结果数据结构
     */
    @Data
    static class TestResult {
        private String query;
        private int retrievedCount;
        private long elapsedMs;
        private List<DocumentChunk> topChunks;
    }
}
