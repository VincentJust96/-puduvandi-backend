package com.puduvandi.booking.controller;

import com.puduvandi.booking.dto.BatchBookingRequest;
import com.puduvandi.booking.dto.BookingResponse;
import com.puduvandi.booking.dto.CancelBookingRequest;
import com.puduvandi.booking.dto.CreateBookingRequest;
import com.puduvandi.booking.dto.PriceEstimateResponse;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Bike booking management")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    // ===== PRICE ESTIMATE (no auth) =====

    @GetMapping("/estimate")
    @Operation(summary = "Get price estimate before booking")
    public ResponseEntity<ApiResponse<PriceEstimateResponse>> estimatePrice(
            @RequestParam Long bikeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime pickupDatetime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime returnDatetime) {

        PriceEstimateResponse estimate = bookingService.estimatePrice(bikeId, pickupDatetime, returnDatetime);
        return ResponseEntity.ok(ApiResponse.success("Price estimate calculated", estimate));
    }

    // ===== CUSTOMER ENDPOINTS =====

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create instant booking (auto-confirmed)")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody CreateBookingRequest request) {

        BookingResponse booking = bookingService.createBooking(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking confirmed!", booking));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Book one or more bikes for the same trip (cart checkout, all-or-nothing)")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> createBookingBatch(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody BatchBookingRequest request) {

        List<BookingResponse> bookings = bookingService.createBookingBatch(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking confirmed!", bookings));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get my bookings")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getMyBookings(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<BookingResponse> bookings = bookingService.getMyBookings(principal.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Bookings fetched", bookings));
    }

    /**
     * Handles both numeric ID (from getBookingById) and reference string (e.g. PV-20240601-0001).
     */
    @GetMapping("/{reference}")
    @Operation(summary = "Get booking by ID or reference number")
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(@PathVariable String reference) {
        BookingResponse booking;
        try {
            booking = bookingService.getBookingById(Long.parseLong(reference));
        } catch (NumberFormatException e) {
            booking = bookingService.getBookingByReference(reference);
        }
        return ResponseEntity.ok(ApiResponse.success("Booking fetched", booking));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Pay for a booking (Phase 3: mocked — booking is already confirmed on create)")
    public ResponseEntity<ApiResponse<BookingResponse>> payBooking(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        BookingResponse booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", booking));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Start ride (CONFIRMED → RIDE_STARTED)")
    public ResponseEntity<ApiResponse<BookingResponse>> startRide(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        BookingResponse booking = bookingService.startRide(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Ride started", booking));
    }

    @PostMapping("/{id}/return-request")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Request bike return (RIDE_STARTED → RETURN_REQUESTED)")
    public ResponseEntity<ApiResponse<BookingResponse>> requestReturn(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        BookingResponse booking = bookingService.requestReturn(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Return requested", booking));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Cancel booking")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CancelBookingRequest request) {

        BookingResponse booking = bookingService.cancelBooking(principal.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled", booking));
    }

    // ===== OWNER ENDPOINTS =====

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Owner confirms booking (Phase 3: mocked — booking auto-confirms on create)")
    public ResponseEntity<ApiResponse<BookingResponse>> confirmBooking(@PathVariable Long id) {
        BookingResponse booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(ApiResponse.success("Booking confirmed", booking));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Owner approves return (RETURN_REQUESTED → COMPLETED)")
    public ResponseEntity<ApiResponse<BookingResponse>> completeBookingAsOwner(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        BookingResponse booking = bookingService.completeBookingAsOwner(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Booking completed", booking));
    }

    // ===== ADMIN ENDPOINTS =====

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin force-completes booking (RETURN_REQUESTED → COMPLETED)")
    public ResponseEntity<ApiResponse<BookingResponse>> completeBooking(@PathVariable Long id) {
        BookingResponse booking = bookingService.completeBooking(id);
        return ResponseEntity.ok(ApiResponse.success("Booking completed", booking));
    }

    @GetMapping("/owner-bookings")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Get bookings for my bikes (alias — prefer GET /owner/bookings)")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getOwnerBookings(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<BookingResponse> bookings = bookingService.getOwnerBookings(principal.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Bookings fetched", bookings));
    }
}
