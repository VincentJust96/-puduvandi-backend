package com.puduvandi.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to manually send a booking-related SMS/WhatsApp message")
public record NotificationRequest(

    @NotNull(message = "Booking ID is required")
    @Schema(example = "1")
    Long bookingId,

    @NotBlank(message = "Phone number is required")
    @Schema(example = "9876543210", description = "10-digit Indian number, optionally with +91 prefix")
    String phone,

    @NotBlank(message = "Message is required")
    @Schema(example = "Your booking PV-20240601-0001 is confirmed.")
    String message

) {}
