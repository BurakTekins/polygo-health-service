package com.polygo.health.common.exception;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

@Getter
@Builder
public class ApiErrorResponse {
    private final String code;
    private final String message;
    private final OffsetDateTime timestamp;
    private final String correlationId;
    private final String path;
}
