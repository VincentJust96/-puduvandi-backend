package com.puduvandi.deposit.dto;

import com.puduvandi.common.enums.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "A deposit deduction claim, for admin review")
public record DepositClaimResponse(
    Long id,
    Long bookingId,
    String bookingReference,
    Long ownerId,
    String ownerName,
    BigDecimal securityDeposit,
    BigDecimal deductionAmount,
    String reason,
    List<String> photoUrls,
    DocumentStatus status,
    String adminRejectionReason,
    LocalDateTime decidedAt,
    LocalDateTime createdAt
) {}
