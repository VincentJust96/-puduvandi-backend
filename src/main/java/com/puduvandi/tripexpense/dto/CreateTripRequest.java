package com.puduvandi.tripexpense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Request to create a new trip")
public record CreateTripRequest(

    @NotBlank(message = "Trip name is required")
    @Schema(example = "Goa • 2026")
    String name,

    @NotNull(message = "Start date is required")
    @Schema(example = "2026-08-26")
    LocalDate startDate,

    @NotNull(message = "End date is required")
    @Schema(example = "2026-08-30")
    LocalDate endDate,

    @NotNull(message = "Budget is required")
    @Positive(message = "Budget must be greater than zero")
    @Schema(example = "75000")
    BigDecimal budget,

    @PositiveOrZero(message = "Contribution can't be negative")
    @Schema(description = "How much the creator is handing over toward the budget right now", example = "15000")
    BigDecimal creatorContributedAmount,

    @Schema(description = "Travelers to invite besides the creator, each with what they're contributing now")
    List<@Valid MemberInput> members

) {
    @Schema(description = "One invited traveler and their upfront contribution toward the trip budget")
    public record MemberInput(
        @NotBlank(message = "Phone number is required")
        @Schema(example = "9876543210")
        String phoneNumber,

        @PositiveOrZero(message = "Contribution can't be negative")
        @Schema(example = "10000")
        BigDecimal contributedAmount
    ) {}
}
