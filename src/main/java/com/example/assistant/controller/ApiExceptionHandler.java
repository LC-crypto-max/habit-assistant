package com.example.assistant.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, Object> validationError(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (left, right) -> left));
        Map<String, Object> body = errorBody("VALIDATION_ERROR", "请求字段校验失败", request);
        body.put("fields", fields);
        return body;
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        String message = safeMessage(exception, "请求参数不合法");
        return errorBody(message, message, request);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Map<String, Object> unreadableRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return errorBody("INVALID_REQUEST_BODY", "请求体格式不正确，请检查 JSON、事件类型和日期格式", request);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Map<String, Object> dataIntegrityError(DataIntegrityViolationException exception,
            HttpServletRequest request) {
        log.warn("Data integrity violation path={} exceptionType={}", request.getRequestURI(),
                exception.getClass().getSimpleName());
        return errorBody("DATA_INTEGRITY_ERROR", "字段过长或格式不符合入库要求", request);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(IllegalStateException.class)
    public Map<String, Object> internalStateError(IllegalStateException exception, HttpServletRequest request) {
        log.error("Backend state error path={} exceptionType={}", request.getRequestURI(),
                exception.getClass().getSimpleName(), exception);
        return errorBody("INTERNAL_STATE_ERROR", "后端执行状态异常，请查看服务器日志", request);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Map<String, Object> unexpectedError(Exception exception, HttpServletRequest request) {
        log.error("Unhandled backend error path={} exceptionType={}", request.getRequestURI(),
                exception.getClass().getSimpleName(), exception);
        return errorBody("INTERNAL_ERROR", "后端处理请求失败，请稍后重试", request);
    }

    private Map<String, Object> errorBody(String error, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message == null || message.isBlank() ? "请求处理失败" : message);
        body.put("path", request.getRequestURI());
        body.put("error", error == null || error.isBlank() ? "BAD_REQUEST" : error);
        return body;
    }

    private String safeMessage(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }
}
