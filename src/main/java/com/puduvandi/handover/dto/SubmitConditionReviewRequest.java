package com.puduvandi.handover.dto;

import com.puduvandi.common.enums.FuelLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Customer's pre-pickup bike condition snapshot")
public record SubmitConditionReviewRequest(
    @NotNull(message = "Fuel level is required")
    FuelLevel fuelLevel,

    @NotNull(message = "Odometer reading is required")
    @Min(value = 0, message = "Odometer reading cannot be negative")
    Integer odometerKm,

    @NotEmpty(message = "At least one bike photo is required")
    @Schema(description = "Bike condition photo URLs, from POST /files/upload")
    List<String> photoUrls
) {}
