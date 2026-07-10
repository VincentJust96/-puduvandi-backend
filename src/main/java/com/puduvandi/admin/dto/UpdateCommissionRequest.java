package com.puduvandi.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Update platform commission percentage")
public record UpdateCommissionRequest(
    @NotNull(message = "Commission percent is required")
    @DecimalMin(value = "0.0", message = "Commission cannot be negative")
    @DecimalMax(value = "50.0", message = "Commission cannot exceed 50%")
    @Schema(example = "20.0")
    BigDecimal commissionPercent
) {}
