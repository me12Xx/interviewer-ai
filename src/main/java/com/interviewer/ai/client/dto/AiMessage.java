package com.interviewer.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiMessage {

    /** system / user / assistant / tool */
    private String role;

    /** assistant 返回 tool_calls 时，content 为 null */
    private String content;

    /** 序列化为 tool_call_id，role:tool 回填时必须带上，让模型知道这条结果对应哪次调用 */
    private String toolCallId;

    /** role:tool 回填时的工具名，部分模型要求此字段 */
    private String name;

    /** role:assistant 且模型调了工具时，toolCalls 非空 */
    private List<ChatResponse.ToolCall> toolCalls;

    /** 兼容旧代码的三参数构造器（system/user/assistant 消息） */
    public AiMessage(String role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
    }

    /** role:tool 回填专用构造器 */
    public static AiMessage toolResult(String toolCallId, String name, String content) {
        AiMessage msg = new AiMessage();
        msg.setRole("tool");
        msg.setContent(content);
        msg.setToolCallId(toolCallId);
        msg.setName(name);
        return msg;
    }

    /** 把 assistant 的响应转成可回填的 AiMessage（保留 toolCalls，让模型看到自己之前调了什么） */
    public static AiMessage fromAssistantResponse(ChatResponse.AssistantMessage am) {
        AiMessage msg = new AiMessage();
        msg.setRole("assistant");
        msg.setContent(am.getContent());
        msg.setToolCalls(am.getToolCalls());
        return msg;
    }
}