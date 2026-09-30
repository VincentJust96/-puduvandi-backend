package com.puduvandi.deposit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Owner's request to deduct part of a completed booking's security deposit")
public record FileDepositClaimRequest(
    @NotNull(message = "Deduction amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Deduction amount must be greater than zero")
    @Schema(example = "500.00")
    BigDecimal deductionAmount,

    @NotBlank(message = "A reason is required")
    @Schema(example = "Scratched fuel tank and a cracked mirror on return")
    String reason,

    @Schema(description = "Optional damage-evidence photo URLs, from POST /files/upload")
    List<String> photoUrls
) {}
