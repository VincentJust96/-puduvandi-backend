package com.puduvandi.payment.dto;

import java.math.BigDecimal;

/**
 * Everything the frontend needs to open the Razorpay checkout widget.
 * razorpayKeyId is the public Key ID — safe to expose to the client.
 */
public record PaymentOrderResponse(
    Long paymentId,
    String razorpayOrderId,
    String razorpayKeyId,
    BigDecimal amount,
    long amountInPaise,
    String currency
) {}
