package com.puduvandi.booking.dto;

import java.math.BigDecimal;

public record PriceEstimateResponse(
    Long bikeId,
    String bikeBrand,
    String bikeModel,
    BigDecimal pricePerHour,
    BigDecimal pricePerDay,
    BigDecimal totalHours,
    BigDecimal totalDays,
    BigDecimal baseAmount,
    BigDecimal securityDeposit,
    BigDecimal totalAmount,
    BigDecimal commissionPercent,
    BigDecimal commissionAmount,
    BigDecimal ownerEarning
) {}
