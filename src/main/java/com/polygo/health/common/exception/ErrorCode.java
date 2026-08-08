package com.polygo.health.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    BAD_REQUEST("BAD_REQUEST", "Bad request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized access", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "Service unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    EXTERNAL_SERVICE_ERROR("EXTERNAL_SERVICE_ERROR", "External service error", HttpStatus.BAD_GATEWAY),
    TIMEOUT_ERROR("TIMEOUT_ERROR", "Request timeout", HttpStatus.GATEWAY_TIMEOUT),
    CONFLICT("CONFLICT", "Resource conflict", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
