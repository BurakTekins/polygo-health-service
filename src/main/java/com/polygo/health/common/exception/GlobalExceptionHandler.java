package com.polygo.health.common.exception;

import com.polygo.health.common.logging.MdcUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Business error: code={} message={} path={}", errorCode.getCode(), ex.getMessage(), request.getRequestURI());
        return buildResponse(errorCode.getCode(), ex.getMessage(), errorCode.getHttpStatus(), request);
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiErrorResponse> handleSystemException(SystemException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.error("System error: code={} message={} path={}", errorCode.getCode(), ex.getMessage(), request.getRequestURI(), ex);
        return buildResponse(errorCode.getCode(), ex.getMessage(), errorCode.getHttpStatus(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");
        log.warn("Validation error: message={} path={}", message, request.getRequestURI());
        return buildResponse(ErrorCode.VALIDATION_ERROR.getCode(), message, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        log.warn("Type mismatch error: message={} path={}", message, request.getRequestURI());
        return buildResponse(ErrorCode.BAD_REQUEST.getCode(), message, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: message={} path={}", ex.getMessage(), request.getRequestURI());
        return buildResponse(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: type={} message={} path={}", ex.getClass().getSimpleName(), ex.getMessage(), request.getRequestURI(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(String code, String message, HttpStatus status, HttpServletRequest request) {
        MdcUtil.setErrorCode(code);
        MdcUtil.setErrorMessage(message);
        MdcUtil.setStatus(status.value());

        ApiErrorResponse response = ApiErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .correlationId(MdcUtil.getCorrelationId())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
