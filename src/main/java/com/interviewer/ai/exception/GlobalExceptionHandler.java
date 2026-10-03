package com.interviewer.ai.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String AI_UNAVAILABLE_MESSAGE = "AI 服务暂时不可用，请稍后重试";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("服务端异常: code={}, message={}", errorCode.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("请求异常: code={}, message={}", errorCode.getCode(), ex.getMessage());
        }

        String message = errorCode == ErrorCode.AI_SERVICE_UNAVAILABLE
                ? AI_UNAVAILABLE_MESSAGE
                : ex.getMessage();
        return response(errorCode, message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiError> handleInvalidRequest(Exception ex) {
        log.warn("请求参数无效: {}", ex.getMessage());
        return response(ErrorCode.INVALID_ARGUMENT, "请求参数无效");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("请求参数异常: {}", ex.getMessage());
        return response(ErrorCode.INVALID_ARGUMENT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("未处理的服务端异常", ex);
        return response(ErrorCode.INTERNAL_ERROR, "服务器内部错误");
    }

    private ResponseEntity<ApiError> response(ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(new ApiError(errorCode.getCode(), message));
    }

    public record ApiError(String code, String message) {
    }
}
