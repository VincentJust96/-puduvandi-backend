package com.puduvandi.booking.dto;

import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DeliveryType;
import com.puduvandi.common.enums.DepositStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    /** Cumulative amount paid so far — 0 until the first payment, partial under the DEPOSIT plan. */
    BigDecimal amountPaid,
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
    DeliveryType deliveryType,
    /**
     * The customer's on-file driving licence, for the owner to review directly —
     * there's no separate admin approval step for it. Null if the customer
     * hasn't uploaded one (or it was later removed).
     */
    String customerLicenceUrl,
    DepositStatus depositStatus,
    /** Set once depositStatus reaches REFUNDED — 0 means the deposit was fully forfeited. */
    BigDecimal depositRefundAmount,
    /** True once the customer has rated this (COMPLETED) trip — always false otherwise. */
    boolean reviewed,
    /**
     * The booked bike's own RC/insurance documents — mirrors BikeResponse's fields so a
     * customer can re-check them from their booking without navigating back to the bike
     * listing. Never includes the insurance PDF's password (admin/super-admin only, see
     * BikeResponse); view it via GET /bikes/{bikeId}/insurance-document instead, which
     * decrypts server-side.
     */
    String rcDocumentUrl,
    String insuranceDocumentUrl,
    String insurancePolicyNumber,
    LocalDate insuranceExpiryDate
) {}
