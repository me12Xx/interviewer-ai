package com.interviewer.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewer.ai.exception.AiServiceException;
import com.interviewer.ai.exception.ApiException;
import com.interviewer.ai.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embedding 服务（调用智谱 AI Embedding API）
 */
@Service
public class EmbeddingService {
    
    @Value("${ai.embedding-api-url}")
    private String embeddingUrl;
    
    @Value("${ai.embedding-api-key}")
    private String apiKey;
    
    @Value("${ai.embedding-model:embedding-3}")
    private String model;
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    public EmbeddingService(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }
    
    public List<Double> embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }
    
    /**
     * 批量向量化：一次请求处理多段文本，避免逐条串行调用
     * 智谱 embedding-3 的 input 支持字符串数组，返回的 data 数组带 index 字段
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null) {
            throw new ApiException(ErrorCode.INVALID_ARGUMENT, "文本列表不能为空");
        }
        if (texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        Map<String, Object> requestBody = Map.of(
            "model", model,
            "input", texts
        );
        
        String response;
        try {
            response = webClient.post()
                .uri(embeddingUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        } catch (Exception e) {
            throw new AiServiceException("Embedding 服务调用失败", e);
        }

        if (response == null || response.isBlank()) {
            throw new AiServiceException("Embedding 服务返回空响应");
        }
        
        try {
            return parseBatchResponse(response, texts.size());
        } catch (Exception e) {
            throw new AiServiceException("Embedding 服务返回无效响应", e);
        }
    }

    private static List<List<Double>> parseBatchResponse(String response, int inputCount) throws Exception {
        JsonNode root = new ObjectMapper().readTree(response);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("响应根节点不是 JSON 对象");
        }
        JsonNode dataArray = root.get("data");
        if (dataArray == null || !dataArray.isArray()) {
            throw new IllegalStateException("响应缺少 data 数组");
        }
        List<List<Double>> result = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            result.add(null);
        }
        for (JsonNode item : dataArray) {
            JsonNode indexNode = item.get("index");
            if (indexNode == null || !indexNode.canConvertToInt()) {
                throw new IllegalStateException("响应缺少有效 index");
            }
            int index = indexNode.intValue();
            if (index < 0 || index >= inputCount) {
                throw new IllegalStateException("响应 index 超出输入范围: " + index);
            }
            JsonNode embeddingNode = item.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new IllegalStateException("响应缺少有效 embedding，index=" + index);
            }
            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                embedding.add(value.asDouble());
            }
            result.set(index, embedding);
        }
        if (result.stream().anyMatch(embedding -> embedding == null)) {
            throw new IllegalStateException("部分输入缺少对应 embedding");
        }
        return result;
    }
}
