package com.interviewer.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewer.ai.client.dto.AiMessage;
import com.interviewer.ai.client.dto.ChatRequest;
import com.interviewer.ai.client.dto.ChatResponse;
import com.interviewer.ai.client.dto.Tool;
import com.interviewer.ai.config.AiConfig;
import com.interviewer.ai.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


import java.util.List;

@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final RestClient restClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    public AiClient(RestClient aiRestClient, AiConfig aiConfig, ObjectMapper objectMapper) {
        this.restClient = aiRestClient;
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
    }

    private ChatResponse doPost(ChatRequest req) {
        try {
            String rawJson = restClient.post()
                    .uri(aiConfig.getApiUrl())
                    .body(req)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                throw new AiServiceException("AI 服务返回空响应");
            }

            ChatResponse response = objectMapper.readValue(rawJson, ChatResponse.class);
            if (response == null) {
                throw new AiServiceException("AI 服务响应解析为 null");
            }
            return response;
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("AI 服务调用或响应解析失败", e);
        }
    }

    public ChatResponse chatWithTools(List<AiMessage> messages, List<Tool> tools) {
        ChatRequest req = ChatRequest.builder()
                .model(aiConfig.getModel())
                .messages(messages)
                .maxTokens(aiConfig.getMaxTokens())
                .temperature(aiConfig.getToolTemperature())
                .tools(tools)
                .toolChoice("required")
                .build();
        return doPost(req);
    }

    public ChatResponse chatWithToolsRetry(
            List<AiMessage> messages,
            List<Tool> tools,
            int maxAttempts) {

        int delayMillis = 1500;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            try {
                ChatResponse response = chatWithTools(messages, tools);

                if (hasValidAssistantMessage(response)) {
                    return response;
                }

                log.warn(
                        "[AiClient] chatWithToolsRetry 第 {} 次返回无效响应，准备重试",
                        attempt + 1
                );
            } catch (Exception e) {
                lastException = e;
                log.warn(
                        "[AiClient] chatWithToolsRetry 第 {} 次异常: {}",
                        attempt + 1,
                        e.getMessage()
                );
            }

            if (attempt < maxAttempts) {
                sleepBeforeRetry(delayMillis);
                delayMillis *= 2;
            }
        }

        if (lastException != null) {
            throw new AiServiceException("AI 服务多次工具调用失败", lastException);
        }

        throw new AiServiceException("AI 服务多次返回无效工具调用响应");
    }

    private boolean hasValidAssistantMessage(ChatResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()) {
            return false;
        }

        ChatResponse.AssistantMessage message =
                response.getChoices().getFirst().getMessage();

        if (message == null) {
            return false;
        }

        boolean hasToolCalls =
                message.getToolCalls() != null
                        && !message.getToolCalls().isEmpty();

        boolean hasContent =
                message.getContent() != null
                        && !message.getContent().isBlank();

        return hasToolCalls || hasContent;
    }

    private void sleepBeforeRetry(int delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("AI 服务重试等待被中断", e);
        }
    }
}