package com.interviewer.ai.service.impl;

sealed interface ToolExecutionResult
        permits ToolExecutionResult.NextQuestion, ToolExecutionResult.Finished,
        ToolExecutionResult.Rejected {

    record NextQuestion(
            String question,
            String proficiencyLevel
    ) implements ToolExecutionResult {
    }

    record Finished(String message) implements ToolExecutionResult {
    }

    record Rejected(
            ToolRejectionReason reason,
            String diagnostic
    ) implements ToolExecutionResult {
    }

    enum ToolRejectionReason {
        MISSING_TOOL_NAME,
        UNKNOWN_TOOL,
        INVALID_ARGUMENTS_JSON,
        MISSING_REQUIRED_FIELD,
        INVALID_FIELD_TYPE,
        INVALID_PROFICIENCY,
        INVALID_FIRST_QUESTION_TOOL,
        FOLLOWUP_LIMIT_REACHED
    }
}
