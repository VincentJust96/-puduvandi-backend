package com.puduvandi.notification.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.notification.dto.NotificationRequest;
import com.puduvandi.notification.service.BookingConfirmationService;
import com.puduvandi.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Manual/ops triggers for booking SMS+WhatsApp notifications.
 * Automatic sends (confirmation on booking creation, completion on return) are
 * wired into BookingService directly — these endpoints are for resending or
 * one-off ops use.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Notifications", description = "Manually trigger or retry booking SMS/WhatsApp notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final BookingConfirmationService bookingConfirmationService;

    @PostMapping("/confirm-booking/{bookingId}")
    @Operation(summary = "Resend the booking confirmation SMS+WhatsApp for a booking")
    public ResponseEntity<ApiResponse<Void>> confirmBooking(@PathVariable Long bookingId) {
        bookingConfirmationService.sendBookingConfirmation(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Booking confirmation notifications sent"));
    }

    @PostMapping("/sms")
    @Operation(summary = "Send a one-off SMS")
    public ResponseEntity<ApiResponse<Void>> sendSms(@Valid @RequestBody NotificationRequest request) {
        notificationService.sendSMS(request.bookingId(), request.phone(), request.message());
        return ResponseEntity.ok(ApiResponse.success("SMS queued for sending"));
    }

    @PostMapping("/whatsapp")
    @Operation(summary = "Send a one-off WhatsApp message")
    public ResponseEntity<ApiResponse<Void>> sendWhatsApp(@Valid @RequestBody NotificationRequest request) {
        notificationService.sendWhatsApp(request.bookingId(), request.phone(), request.message());
        return ResponseEntity.ok(ApiResponse.success("WhatsApp message queued for sending"));
    }

    @PostMapping("/send-both")
    @Operation(summary = "Send the same message via SMS and WhatsApp")
    public ResponseEntity<ApiResponse<Void>> sendBoth(@Valid @RequestBody NotificationRequest request) {
        notificationService.sendBoth(request.bookingId(), request.phone(), request.message());
        return ResponseEntity.ok(ApiResponse.success("SMS and WhatsApp queued for sending"));
    }

    @PostMapping("/pickup-reminder/{bookingId}")
    @Operation(summary = "Send a pickup reminder for a booking")
    public ResponseEntity<ApiResponse<Void>> pickupReminder(@PathVariable Long bookingId) {
        bookingConfirmationService.sendPickupReminder(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Pickup reminder sent"));
    }

    @PostMapping("/ride-completion/{bookingId}")
    @Operation(summary = "Resend the ride completion notification for a booking")
    public ResponseEntity<ApiResponse<Void>> rideCompletion(@PathVariable Long bookingId) {
        bookingConfirmationService.sendRideCompletionNotification(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Ride completion notification sent"));
    }

    @PostMapping("/retry-failed")
    @Operation(summary = "Retry all FAILED notifications with fewer than 3 attempts")
    public ResponseEntity<ApiResponse<Void>> retryFailed() {
        notificationService.retryFailedNotifications();
        return ResponseEntity.ok(ApiResponse.success("Retry process completed"));
    }
}
