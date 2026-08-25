package com.puduvandi.tripexpense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to reposition the trip cover image's vertical focal point")
public record SetTripCoverPositionRequest(

    @NotNull(message = "positionY is required")
    @Min(value = 0, message = "positionY must be between 0 and 100")
    @Max(value = 100, message = "positionY must be between 0 and 100")
    Integer positionY

) {}
