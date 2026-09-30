package com.puduvandi.handover.dto;

import com.puduvandi.common.enums.FuelLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "A customer's submitted pre-pickup bike condition review")
public record ConditionReviewResponse(
    Long id,
    Long bookingId,
    FuelLevel fuelLevel,
    Integer odometerKm,
    List<String> photoUrls,
    LocalDateTime createdAt
) {}
