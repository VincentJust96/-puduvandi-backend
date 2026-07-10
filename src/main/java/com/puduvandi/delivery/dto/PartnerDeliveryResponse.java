package com.puduvandi.delivery.dto;

import com.puduvandi.common.enums.DeliveryStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PartnerDeliveryResponse(
    Long id,
    Long bookingId,
    String bookingReference,
    String bikeBrand,
    String bikeModel,
    BigDecimal pickupLatitude,
    BigDecimal pickupLongitude,
    BigDecimal dropoffLatitude,
    BigDecimal dropoffLongitude,
    BigDecimal distanceKm,
    BigDecimal deliveryFee,
    DeliveryStatus status,
    String customerName,
    String customerPhone,
    LocalDateTime createdAt,
    LocalDateTime claimedAt,
    LocalDateTime pickedUpAt,
    LocalDateTime deliveredAt
) {}
