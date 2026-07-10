package com.puduvandi.owner.dto;

import com.puduvandi.common.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Upload an owner KYC document")
public record UploadOwnerDocumentRequest(

    @NotNull(message = "Document type is required")
    @Schema(example = "AADHAAR")
    DocumentType documentType,

    @NotBlank(message = "Document URL is required")
    String documentUrl

) {}
