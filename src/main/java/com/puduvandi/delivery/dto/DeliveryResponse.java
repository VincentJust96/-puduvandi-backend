package com.puduvandi.delivery.dto;

import com.puduvandi.common.enums.DeliveryStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeliveryResponse(
    Long id,
    DeliveryStatus status,
    BigDecimal distanceKm,
    BigDecimal deliveryFee,
    String partnerName,
    String partnerPhone,
    LocalDateTime claimedAt,
    LocalDateTime pickedUpAt,
    LocalDateTime deliveredAt
) {}
