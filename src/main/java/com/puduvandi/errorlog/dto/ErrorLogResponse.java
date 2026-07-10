package com.puduvandi.errorlog.dto;

import java.time.LocalDateTime;

public record ErrorLogResponse(
    Long id,
    String severity,
    String errorCode,
    String errorMessage,
    String source,
    String entityType,
    Long entityId,
    Long userId,
    String requestPath,
    String requestMethod,
    String context,
    String stackTrace,
    LocalDateTime createdAt
) {}
