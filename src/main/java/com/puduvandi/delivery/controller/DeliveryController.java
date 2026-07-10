package com.puduvandi.delivery.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.delivery.dto.DeliveryRateResponse;
import com.puduvandi.delivery.dto.DeliveryResponse;
import com.puduvandi.delivery.service.DeliveryService;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@Tag(name = "Delivery", description = "Customer/owner-facing view of a booking's partner delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping("/api/v1/delivery/rate")
    @Operation(summary = "Get the current per-km partner delivery rate (for checkout fee estimates)")
    public ResponseEntity<ApiResponse<BigDecimal>> getRate() {
        return ResponseEntity.ok(ApiResponse.success("Delivery rate fetched", deliveryService.getActiveRate()));
    }

    @GetMapping("/api/v1/bookings/{id}/delivery")
    @PreAuthorize("hasAnyRole('OWNER', 'CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "View the partner delivery status for a booking")
    public ResponseEntity<ApiResponse<DeliveryResponse>> getBookingDelivery(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        DeliveryResponse response = deliveryService.getDeliveryForBooking(id, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Delivery fetched", response));
    }
}
