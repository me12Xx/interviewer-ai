package com.interviewer.ai.exception;

/**
 * AI 服务异常：网络超时、反序列化失败、返回空响应等。
 * 由后续的 GlobalExceptionHandler 统一处理。
 */
public class AiServiceException extends ApiException {

    public AiServiceException(String message) {
        super(ErrorCode.AI_SERVICE_UNAVAILABLE, message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(ErrorCode.AI_SERVICE_UNAVAILABLE, message, cause);
    }
}
