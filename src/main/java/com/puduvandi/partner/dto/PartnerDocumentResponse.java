package com.puduvandi.partner.dto;

import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Delivery partner uploaded document details")
public record PartnerDocumentResponse(
    Long id,
    DocumentType documentType,
    String documentUrl,
    DocumentStatus status,
    String remarks,
    LocalDateTime createdAt
) {}
