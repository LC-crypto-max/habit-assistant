package com.example.assistant.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, Object> validationError(MethodArgumentNotValidException exception) {
        return Map.of(
                "error", "VALIDATION_ERROR",
                "fields", exception.getBindingResult().getFieldErrors().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                FieldError::getField,
                                error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                                (left, right) -> left)));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, String> badRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Map<String, String> unreadableRequest(HttpMessageNotReadableException exception) {
        return Map.of(
                "error", "INVALID_REQUEST_BODY",
                "message", "请求体格式不正确，请检查 JSON 和日期格式。");
    }
}
