package com.puduvandi.booking.dto;

import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DeliveryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
    Long id,
    String bookingReference,
    Long customerId,
    String customerName,
    Long bikeId,
    String bikeBrand,
    String bikeModel,
    String bikeRegistrationNumber,
    LocalDateTime pickupDatetime,
    LocalDateTime returnDatetime,
    LocalDateTime actualReturnDatetime,
    BigDecimal totalHours,
    BigDecimal totalDays,
    BigDecimal baseAmount,
    BigDecimal securityDeposit,
    BigDecimal totalAmount,
    BigDecimal commissionPercent,
    BigDecimal commissionAmount,
    BigDecimal ownerEarning,
    BookingStatus status,
    boolean helmetIncluded,
    String cancellationReason,
    LocalDateTime createdAt,
    /** Derived: DAY if booked >= 24h, else HOUR */
    String rentalMode,
    /** Derived: number of days or hours matching rentalMode */
    int quantity,
    DeliveryType deliveryType
) {}
