package com.puduvandi.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Update the per-km partner delivery rate")
public record UpdateDeliveryRateRequest(
    @NotNull(message = "Rate per km is required")
    @DecimalMin(value = "0.0", message = "Rate cannot be negative")
    @DecimalMax(value = "1000.0", message = "Rate cannot exceed 1000")
    @Schema(example = "15.00")
    BigDecimal ratePerKm
) {}
