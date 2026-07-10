package com.puduvandi.user.dto;

import com.puduvandi.common.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to upload a document URL.
 * File is uploaded to cloud storage first; URL is sent here.
 */
@Schema(description = "Upload a document")
public record UploadDocumentRequest(

    @NotNull(message = "Document type is required")
    @Schema(example = "DRIVING_LICENSE")
    DocumentType documentType,

    @NotBlank(message = "Document URL is required")
    @Schema(example = "https://cdn.puduvandi.com/docs/license-ravi.jpg")
    String documentUrl

) {}
