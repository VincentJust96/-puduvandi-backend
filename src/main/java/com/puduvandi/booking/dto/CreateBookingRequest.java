package com.puduvandi.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Request to create a new booking")
public record CreateBookingRequest(

    @NotNull(message = "Bike ID is required")
    @Schema(example = "1")
    Long bikeId,

    @NotNull(message = "Pickup datetime is required")
    @Future(message = "Pickup datetime must be in the future")
    @Schema(example = "2024-07-01T09:00:00")
    LocalDateTime pickupDatetime,

    @NotNull(message = "Return datetime is required")
    @Future(message = "Return datetime must be in the future")
    @Schema(example = "2024-07-02T09:00:00")
    LocalDateTime returnDatetime,

    @Pattern(regexp = "SELF_PICKUP|PARTNER_DELIVERY", message = "Delivery type must be SELF_PICKUP or PARTNER_DELIVERY")
    @Schema(example = "SELF_PICKUP", description = "Defaults to SELF_PICKUP when absent")
    String deliveryType,

    @Schema(description = "Customer's chosen drop-off point — required when deliveryType is PARTNER_DELIVERY", example = "12.9689")
    BigDecimal dropoffLatitude,

    @Schema(example = "79.8612")
    BigDecimal dropoffLongitude

) {}
