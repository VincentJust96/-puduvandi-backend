package com.puduvandi.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Customer's one-time GPS location shared for a booking")
public record LocationRequest(

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90", message = "Latitude must be >= -90")
    @DecimalMax(value = "90", message = "Latitude must be <= 90")
    @Schema(example = "12.9716")
    BigDecimal latitude,

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180", message = "Longitude must be >= -180")
    @DecimalMax(value = "180", message = "Longitude must be <= 180")
    @Schema(example = "79.8589")
    BigDecimal longitude

) {}
