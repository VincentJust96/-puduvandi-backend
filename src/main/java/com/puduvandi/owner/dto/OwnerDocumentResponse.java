package com.puduvandi.owner.dto;

import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Owner uploaded document details")
public record OwnerDocumentResponse(
    Long id,
    DocumentType documentType,
    String documentUrl,
    DocumentStatus status,
    String remarks,
    LocalDateTime createdAt
) {}
