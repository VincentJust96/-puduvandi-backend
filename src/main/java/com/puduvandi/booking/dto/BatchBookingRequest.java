package com.puduvandi.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Creates one booking per bikeId for a shared trip in a single checkout.
 * Matches the frontend cart payload.
 */
@Schema(description = "Request to book one or more bikes for the same trip")
public record BatchBookingRequest(

    @NotEmpty(message = "At least one bike must be selected")
    @Schema(example = "[1, 4]")
    List<Long> bikeIds,

    @NotNull(message = "Start date is required")
    @Schema(example = "2026-07-05")
    LocalDate startDate,

    @Schema(example = "2026-07-07", description = "Same as startDate for HOUR mode")
    LocalDate endDate,

    @NotNull(message = "Rental mode is required")
    @Pattern(regexp = "DAY|HOUR", message = "Rental mode must be DAY or HOUR")
    @Schema(example = "DAY")
    String rentalMode,

    @NotNull @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(example = "2", description = "Number of days or hours depending on rentalMode")
    Integer quantity,

    @Schema(description = "Driving licence URL — optional, upload flow bypassed for now")
    String documentUrl,

    @Schema(example = "true")
    Boolean helmetIncluded,

    @Pattern(regexp = "SELF_PICKUP|PARTNER_DELIVERY", message = "Delivery type must be SELF_PICKUP or PARTNER_DELIVERY")
    @Schema(example = "SELF_PICKUP", description = "Defaults to SELF_PICKUP when absent")
    String deliveryType,

    @Schema(description = "Customer's chosen drop-off point — required when deliveryType is PARTNER_DELIVERY", example = "12.9689")
    BigDecimal dropoffLatitude,

    @Schema(example = "79.8612")
    BigDecimal dropoffLongitude

) {}
