package com.puduvandi.payment.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Bookings created together in one checkout trip that should be paid for as a single order. */
public record CreatePaymentOrderRequest(
    @NotEmpty(message = "At least one booking is required")
    List<Long> bookingIds
) {}
