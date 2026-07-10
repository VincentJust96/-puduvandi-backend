package com.puduvandi.user.dto;

import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response for an uploaded document.
 */
@Schema(description = "Uploaded document details")
public record UserDocumentResponse(
    Long id,
    DocumentType documentType,
    String documentUrl,
    DocumentStatus status,
    String remarks,
    LocalDateTime createdAt
) {}
