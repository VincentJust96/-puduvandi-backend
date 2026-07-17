package com.puduvandi.delivery.controller;

import com.puduvandi.booking.dto.LocationRequest;
import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.delivery.dto.PartnerDeliveryResponse;
import com.puduvandi.delivery.service.DeliveryService;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/partner/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTNER')")
@Tag(name = "Partner", description = "Delivery partner — claim and fulfil bike delivery jobs")
@SecurityRequirement(name = "bearerAuth")
public class PartnerController {

    private final DeliveryService deliveryService;

    @GetMapping("/available")
    @Operation(summary = "List unclaimed delivery jobs")
    public ResponseEntity<ApiResponse<List<PartnerDeliveryResponse>>> getAvailable() {
        return ResponseEntity.ok(ApiResponse.success("Available deliveries fetched", deliveryService.listAvailable()));
    }

    @GetMapping("/my")
    @Operation(summary = "List this partner's claimed deliveries (active + history)")
    public ResponseEntity<ApiResponse<List<PartnerDeliveryResponse>>> getMyDeliveries(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success("My deliveries fetched", deliveryService.getMyDeliveries(principal.getUserId())));
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "Claim an unclaimed delivery job")
    public ResponseEntity<ApiResponse<PartnerDeliveryResponse>> claim(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.success("Delivery claimed", deliveryService.claim(principal.getUserId(), id)));
    }

    @PostMapping("/{id}/location")
    @Operation(summary = "Push my current GPS location while a claimed delivery leg is in progress")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody LocationRequest request) {

        deliveryService.updatePartnerLocation(principal.getUserId(), id, request.latitude(), request.longitude());
        return ResponseEntity.ok(ApiResponse.success("Location updated"));
    }

    // NOTE: "picked up" (CLAIMED → PICKED_UP) and "delivered" (PICKED_UP → DELIVERED) are no
    // longer bare endpoints — they now require OTP handover verification. See
    // HandoverOtpController (POST /api/v1/bookings/{id}/handover/pickup_partner/generate +
    // /verify, and /handover/receive_partner/generate + /verify respectively).
}
