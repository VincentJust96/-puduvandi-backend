package com.puduvandi.admin.dto;

import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Admin-facing view of an uploaded document, including who it belongs to.
 */
@Schema(description = "Uploaded document details, for admin review")
public record AdminUserDocumentResponse(
    Long id,
    Long userId,
    String userFullName,
    String userPhoneNumber,
    DocumentType documentType,
    String documentUrl,
    DocumentStatus status,
    String remarks,
    LocalDateTime createdAt
) {}
