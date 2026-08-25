package com.puduvandi.tripexpense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Request to invite a traveler to a trip by phone number")
public record AddMemberRequest(

    @NotBlank(message = "Phone number is required")
    @Schema(example = "9876543210")
    String phoneNumber,

    @PositiveOrZero(message = "Contribution can't be negative")
    @Schema(description = "How much this traveler is contributing toward the budget right now", example = "10000")
    BigDecimal contributedAmount

) {}
