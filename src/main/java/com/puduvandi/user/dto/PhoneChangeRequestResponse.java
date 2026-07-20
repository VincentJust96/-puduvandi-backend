package com.puduvandi.user.dto;

import com.puduvandi.common.enums.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A phone number change request awaiting (or resolved by) admin review")
public record PhoneChangeRequestResponse(
    Long id,
    Long userId,
    String userFullName,
    String oldPhoneNumber,
    String newPhoneNumber,
    DocumentStatus status,
    String remarks,
    LocalDateTime createdAt
) {}
