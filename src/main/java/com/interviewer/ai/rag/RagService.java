package com.interviewer.ai.rag;

import com.interviewer.ai.service.TypeSafeClient;
import com.interviewer.ai.service.TypeSafeClient.ScoreQuestion;
import com.interviewer.ai.service.TypeSafeClient.SystemOneResponse;
import com.interviewer.ai.service.TypeSafeClient.ScoreAnswer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class RagService {
    
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseStorage storage;
    private final TypeSafeClient typeSafeClient;
    
    private List<DocumentChunk> knowledgeBase = new ArrayList<>();
    
    public RagService(
        EmbeddingService embeddingService,
        KnowledgeBaseStorage storage,
        TypeSafeClient typeSafeClient
    ) {
        this.embeddingService = embeddingService;
        this.storage = storage;
        this.typeSafeClient = typeSafeClient;
    }
    
    public void loadKnowledgeBase() throws IOException {
        knowledgeBase = storage.load();
        log.info("RAG 服务已加载知识库，共 {} 个文档块", knowledgeBase.size());
    }
    
    public List<DocumentChunk> retrieve(String query, int topK, String position) throws IOException {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }

        if (knowledgeBase.isEmpty()) {
            log.warn("知识库为空，无法检索");
            return new ArrayList<>();
        }

        List<DocumentChunk> filteredKnowledgeBase = knowledgeBase.stream()
                .filter(chunk -> position.equals(chunk.getPosition()))
                .toList();
        if (filteredKnowledgeBase.isEmpty()) {
            log.warn("岗位 {} 没有可用知识库文档，无法检索", position);
            return new ArrayList<>();
        }
        
        // 阶段1：余弦相似度召回（召回 topK * 3 个候选）
        List<Double> queryEmbedding = embeddingService.embed(query);
        
        for (DocumentChunk chunk : filteredKnowledgeBase) {
            double similarity = cosineSimilarity(queryEmbedding, chunk.getEmbedding());
            chunk.setScore(similarity);
        }

        if (!typeSafeClient.isAvailable()) {
            log.debug("Jev 未启用，使用单阶段余弦 TopK 检索");
            return filteredKnowledgeBase.stream()
                    .sorted(Comparator.comparingDouble(DocumentChunk::getScore).reversed())
                    .limit(topK)
                    .toList();
        }

        int recallSize = Math.min(topK * 3, filteredKnowledgeBase.size());
        List<DocumentChunk> candidates = filteredKnowledgeBase.stream()
                .sorted(Comparator.comparingDouble(DocumentChunk::getScore).reversed())
                .limit(recallSize)
                .toList();
        
        log.debug("余弦相似度召回 {} 个候选文档", candidates.size());
        
        // 阶段2：Jev 精排（如果可用）
        List<DocumentChunk> reranked = rerankWithJev(query, candidates);

        return reranked.stream()
                .limit(topK)
                .toList();
    }
    
    /**
     * 使用 TypeSafe Jev 模型对候选文档进行重排序
     * 
     * @param query 用户查询
     * @param candidates 召回的候选文档
     * @return 重排序后的文档列表
     */
    private List<DocumentChunk> rerankWithJev(String query, List<DocumentChunk> candidates) {
        if (!typeSafeClient.isAvailable()) {
            log.debug("Jev 重排序未启用，使用原始余弦相似度排序");
            return candidates;
        }
        
        if (candidates.isEmpty()) {
            return candidates;
        }
        
        try {
            // 构造 state（查询上下文）
            Map<String, Object> state = new HashMap<>();
            state.put("query", query);
            
            // 构造并行问题（每个候选文档一个 Score 问题）
            Map<String, ScoreQuestion> questions = new HashMap<>();
            for (int i = 0; i < candidates.size(); i++) {
                String chunkKey = "chunk_" + i;
                DocumentChunk chunk = candidates.get(i);
                
                // 将文档内容嵌入到 state 中
                state.put(chunkKey, chunk.getText());
                
                // 创建评分问题
                questions.put(chunkKey, new ScoreQuestion(
                    "How relevant is the passage in `" + chunkKey
                        + "` to answering the interview question in `query`? Evaluate only this passage.",
                    new String[]{"not_relevant", "somewhat_relevant", "highly_relevant"}
                ));
            }
            
            // 单次请求并行评估所有文档
            long startTime = System.currentTimeMillis();
            SystemOneResponse response = typeSafeClient.systemOne(state, questions);
            long elapsed = System.currentTimeMillis() - startTime;
            
            if (response == null) {
                log.warn("Jev 重排序失败，回退到原始排序");
                return candidates;
            }
            
            // 根据 Jev 的评分重新排序
            List<ScoredChunk> scored = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                String chunkKey = "chunk_" + i;
                ScoreAnswer scoreAnswer = response.getScore(chunkKey);
                
                if (scoreAnswer != null) {
                    double relevanceScore = scoreAnswer.getScore();
                    double confidence = scoreAnswer.getConfidence();
                    double finalScore = relevanceScore * confidence;
                    
                    scored.add(new ScoredChunk(candidates.get(i), finalScore, relevanceScore, confidence));
                } else {
                    // 如果某个文档没有评分结果，保留原始余弦相似度分数
                    scored.add(new ScoredChunk(candidates.get(i), candidates.get(i).getScore(), 0, 0.0));
                }
            }
            
            // 按最终分数降序排序
            scored.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
            
            log.info("Jev 重排序完成 ({}ms)，候选数: {}", elapsed, candidates.size());
            
            // 更新 chunk 的 score 为 Jev 的最终分数（用于日志展示）
            List<DocumentChunk> result = new ArrayList<>();
            for (ScoredChunk sc : scored) {
                sc.chunk.setScore(sc.finalScore);
                result.add(sc.chunk);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Jev 重排序异常，回退到原始排序", e);
            return candidates;
        }
    }
    
    /**
     * 将 Jev 的评分等级转换为数值
     */
    /**
     * 内部类：带评分的文档块
     */
    private static class ScoredChunk {
        DocumentChunk chunk;
        double finalScore;
        double relevanceScore;
        double confidence;
        
        ScoredChunk(DocumentChunk chunk, double finalScore, double relevanceScore, double confidence) {
            this.chunk = chunk;
            this.finalScore = finalScore;
            this.relevanceScore = relevanceScore;
            this.confidence = confidence;
        }
    }
    
    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1.size() != v2.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    public String buildContext(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("以下是相关知识库内容：\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            sb.append(String.format("[%d] (相似度: %.3f, 来源: %s)\n%s\n\n", 
                    i + 1, chunk.getScore(), chunk.getSource(), chunk.getText()));
        }
        return sb.toString();
    }
}
