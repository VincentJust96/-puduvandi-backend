package com.puduvandi.deposit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "A booking whose deposit refund attempt failed against Razorpay — retryable by an admin")
public record FailedRefundResponse(
    Long bookingId,
    String bookingReference,
    BigDecimal securityDeposit,
    BigDecimal attemptedAmount,
    LocalDateTime lastAttemptedAt
) {}
