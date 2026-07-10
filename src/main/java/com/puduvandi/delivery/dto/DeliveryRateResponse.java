package com.puduvandi.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Current partner delivery rate")
public record DeliveryRateResponse(
    Long id,
    BigDecimal ratePerKm,
    String updatedByAdminName,
    LocalDateTime updatedAt
) {}
