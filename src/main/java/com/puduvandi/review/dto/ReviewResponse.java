package com.puduvandi.review.dto;

import java.time.LocalDateTime;

public record ReviewResponse(
    Long id,
    Long bookingId,
    Long bikeId,
    Integer rating,
    String comment,
    LocalDateTime createdAt
) {}
