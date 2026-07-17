package com.puduvandi.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Confirmation payload for the local data reset (DANGER: wipes all non-admin data)")
public record ResetLocalDataRequest(
    @NotBlank(message = "confirmationPhrase is required")
    @Schema(example = "RESET_ALL_DATA", description = "Must equal exactly \"RESET_ALL_DATA\" to proceed")
    String confirmationPhrase
) {}
