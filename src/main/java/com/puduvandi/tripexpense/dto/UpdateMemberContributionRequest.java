package com.puduvandi.tripexpense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Request to update how much a traveler has contributed toward the trip budget so far")
public record UpdateMemberContributionRequest(

    @NotNull(message = "Contribution amount is required")
    @PositiveOrZero(message = "Contribution can't be negative")
    @Schema(example = "15000")
    BigDecimal contributedAmount

) {}
