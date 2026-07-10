package com.puduvandi.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Rejection reason for KYC or bike approval")
public record RejectReasonRequest(
    @NotBlank(message = "Rejection reason is required")
    @Schema(example = "Documents are blurry or incomplete")
    String reason
) {}
