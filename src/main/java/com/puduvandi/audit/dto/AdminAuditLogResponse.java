package com.puduvandi.audit.dto;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        Long id,
        Long actorUserId,
        String actorName,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        String beforeState,
        String afterState,
        LocalDateTime createdAt
) {}
