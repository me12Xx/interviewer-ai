package com.interviewer.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatRequest {

    private String model;

    private List<AiMessage> messages;

    /** 可选：JSON 模式，传 Map.of("type","json_object") 强制模型返回 JSON */
    private Object responseFormat;

    /** 序列化为 max_tokens */
    private Integer maxTokens;

    private Double temperature;

    /** 工具列表，为 null 时不会出现在 JSON 里 */
    private List<Tool> tools;

    /** 工具选择策略：auto（自动决定）/ required（必须调工具）/ none（不调工具） */
    private String toolChoice;
    
    /** 是否开启流式响应 */
    private Boolean stream;
}