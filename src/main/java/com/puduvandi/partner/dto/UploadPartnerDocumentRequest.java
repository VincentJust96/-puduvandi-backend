package com.puduvandi.partner.dto;

import com.puduvandi.common.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Upload a delivery partner KYC document")
public record UploadPartnerDocumentRequest(

    @NotNull(message = "Document type is required")
    @Schema(example = "DRIVING_LICENSE")
    DocumentType documentType,

    @NotBlank(message = "Document URL is required")
    String documentUrl

) {}
