package com.interviewer.ai.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.interviewer.ai.config.InterviewConstants.ANSWER_MAX_LENGTH;

public record ChatRequest(
        @NotBlank(message = "回答不能为空")
        @Size(max = ANSWER_MAX_LENGTH, message = "回答长度不能超过 5000 字符") String answer,
        @NotBlank(message = "requestId 不能为空") String requestId) {
}
