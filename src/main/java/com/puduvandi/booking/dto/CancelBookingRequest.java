package com.puduvandi.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to cancel a booking")
public record CancelBookingRequest(

    @NotBlank(message = "Cancellation reason is required")
    @Schema(example = "Change of travel plans")
    String reason

) {}
