package com.puduvandi.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A review, as seen by an admin for moderation")
public record AdminReviewResponse(
    Long id,
    Long bookingId,
    Long bikeId,
    String bikeLabel,
    Long customerId,
    String customerName,
    Integer rating,
    String comment,
    LocalDateTime createdAt
) {}
