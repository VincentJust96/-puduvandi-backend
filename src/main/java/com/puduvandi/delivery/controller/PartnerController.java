package com.puduvandi.delivery.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.delivery.dto.PartnerDeliveryResponse;
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

    @PostMapping("/{id}/picked-up")
    @Operation(summary = "Mark the bike as picked up from the owner")
    public ResponseEntity<ApiResponse<PartnerDeliveryResponse>> markPickedUp(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.success("Marked picked up", deliveryService.markPickedUp(principal.getUserId(), id)));
    }

    @PostMapping("/{id}/delivered")
    @Operation(summary = "Mark the bike as delivered to the customer")
    public ResponseEntity<ApiResponse<PartnerDeliveryResponse>> markDelivered(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.success("Marked delivered", deliveryService.markDelivered(principal.getUserId(), id)));
    }
}
