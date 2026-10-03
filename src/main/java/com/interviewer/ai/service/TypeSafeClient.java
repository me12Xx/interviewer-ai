package com.interviewer.ai.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * TypeSafe AI（Jev 模型）客户端
 * 官方文档：https://docs.typesafe.ai/
 * 
 * 用途：RAG 检索结果重排序
 * - 将余弦相似度检索的候选文档发送给 Jev
 * - Jev 基于语义相关性进行精准评分
 * - 根据评分和置信度重新排序
 */
@Slf4j
@Service
public class TypeSafeClient {
    
    private static final String API_URL = "https://api.typesafe.ai/v1/systemone";
    private static final String MODEL = "jev-latest";
    
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final boolean enabled;
    
    public TypeSafeClient(
        @Value("${typesafe.api-key:}") String apiKey,
        @Value("${typesafe.enabled:false}") boolean enabled,
        RestTemplate restTemplate
    ) {
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.restTemplate = restTemplate;
        
        if (enabled && (apiKey == null || apiKey.isEmpty())) {
            log.warn("TypeSafe is enabled but API key is not configured");
        }
    }
    
    /**
     * 检查 TypeSafe 是否可用
     */
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }
    
    /**
     * 调用 System One API（Score 原语）
     * 
     * @param state 查询上下文（包含用户问题/查询）
     * @param questions Score 问题集合（每个文档块对应一个评分问题）
     * @return API 响应
     */
    public SystemOneResponse systemOne(Map<String, Object> state, Map<String, ScoreQuestion> questions) {
        if (!isAvailable()) {
            log.debug("TypeSafe not available, skipping call");
            return null;
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL);
            requestBody.put("state", state);
            requestBody.put("questions", convertQuestions(questions));
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            long startTime = System.currentTimeMillis();
            ResponseEntity<SystemOneResponse> response = restTemplate.postForEntity(
                API_URL, request, SystemOneResponse.class
            );
            long elapsed = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.debug("TypeSafe API call successful ({}ms)", elapsed);
                return response.getBody();
            } else {
                log.warn("TypeSafe API returned status: {}", response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("TypeSafe API call failed", e);
            return null;
        }
    }
    
    /**
     * 将 ScoreQuestion 转换为 API 请求格式
     */
    private Map<String, Map<String, Object>> convertQuestions(Map<String, ScoreQuestion> questions) {
        Map<String, Map<String, Object>> converted = new HashMap<>();
        
        questions.forEach((key, question) -> {
            Map<String, Object> q = new HashMap<>();
            q.put("type", "score");
            q.put("instructions", question.getInstructions());
            q.put("criteria", question.getCriteria());
            
            converted.put(key, q);
        });
        
        return converted;
    }
    
    // ==================== 数据结构 ====================
    
    /**
     * Score 问题：梯度评分（有序列表）
     */
    @Data
    public static class ScoreQuestion {
        private final String instructions;
        private final String[] criteria;
        
        public ScoreQuestion(String instructions, String[] criteria) {
            this.instructions = instructions;
            this.criteria = criteria;
        }
    }
    
    /**
     * API 响应
     */
    @Data
    public static class SystemOneResponse {
        private Map<String, ScoreAnswer> answers;
        
        /**
         * 获取指定 key 的评分结果
         */
        public ScoreAnswer getScore(String key) {
            return answers != null ? answers.get(key) : null;
        }
    }
    
    /**
     * Score 答案
     */
    @Data
    public static class ScoreAnswer {
        private String type;
        
        /**
         * 评分结果（criteria 中的某一项）
         */
        private String score;
        
        /**
         * 置信度（0~1，越高越可信）
         */
        private Double confidence;
        
        /**
         * 每个等级的概率分布
         */
        @JsonProperty("probabilities")
        private Map<String, Double> probabilities;
    }
}
