package com.interviewer.ai;

import com.interviewer.ai.client.AiClient;
import com.interviewer.ai.client.dto.AiMessage;
import com.interviewer.ai.client.dto.ChatResponse;
import com.interviewer.ai.client.dto.Tool;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class FunctionCallingTest {

    @Autowired
    private AiClient aiClient;

    @Test
    void testToolCallSupport() {
        // 构建工具定义
        Tool tool = Tool.builder()
                .type("function")
                .function(Tool.FunctionDef.builder()
                        .name("get_weather")
                        .description("获取指定城市的天气")
                        .parameters(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "city", Map.of(
                                                "type", "string",
                                                "description", "城市名"
                                        )
                                ),
                                "required", List.of("city")
                        ))
                        .build())
                .build();

        List<AiMessage> messages = List.of(
                new AiMessage("user", "北京今天天气怎么样？", null)
        );

        ChatResponse resp = aiClient.chatWithTools(messages, List.of(tool));

        log.info("========== Function Calling 诊断结果 ==========");
        log.info("Response 是否为 null: {}", resp == null);
        if (resp != null && resp.getChoices() != null && !resp.getChoices().isEmpty()) {
            var choice = resp.getChoices().get(0);
            log.info("finishReason: {}", choice.getFinishReason());
            log.info("content: {}", choice.getMessage().getContent());
        }

        assertNotNull(resp);
        assertNotNull(resp.getChoices());
        assertFalse(resp.getChoices().isEmpty());
        var message = resp.getChoices().get(0).getMessage();
        assertNotNull(message);
        assertNotNull(message.getToolCalls());
        assertFalse(message.getToolCalls().isEmpty(), "DeepSeek API 未返回 required 工具调用");
        assertNotNull(message.getToolCalls().get(0).getFunction());
        assertEquals("get_weather", message.getToolCalls().get(0).getFunction().getName());
        log.info("调用的工具: {}", message.getToolCalls().get(0).getFunction().getName());
    }

}
