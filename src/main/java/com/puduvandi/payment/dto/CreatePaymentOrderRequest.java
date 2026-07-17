package com.puduvandi.payment.dto;

import com.puduvandi.common.enums.PaymentType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Bookings created together in one checkout trip that should be paid for as a single order. */
public record CreatePaymentOrderRequest(
    @NotEmpty(message = "At least one booking is required")
    List<Long> bookingIds,

    @NotNull(message = "Payment type is required")
    PaymentType paymentType
) {}
