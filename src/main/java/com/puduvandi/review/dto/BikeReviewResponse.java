package com.puduvandi.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A single review shown on a bike's public listing")
public record BikeReviewResponse(
    Long id,
    @Schema(example = "Arun K.") String customerName,
    Integer rating,
    String comment,
    LocalDateTime createdAt
) {}
