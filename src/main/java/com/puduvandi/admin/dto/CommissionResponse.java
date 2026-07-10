package com.puduvandi.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Current commission settings")
public record CommissionResponse(
    Long id,
    BigDecimal commissionPercent,
    String updatedByAdminName,
    LocalDateTime updatedAt
) {}
