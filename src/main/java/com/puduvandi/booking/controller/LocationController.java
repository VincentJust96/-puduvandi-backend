package com.puduvandi.booking.controller;

import com.puduvandi.booking.dto.LocationRequest;
import com.puduvandi.booking.dto.LocationResponse;
import com.puduvandi.booking.dto.TrackingResponse;
import com.puduvandi.booking.service.LocationService;
import com.puduvandi.booking.service.TrackingService;
import com.puduvandi.common.dto.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Location", description = "One-time customer location sharing for a booking")
@SecurityRequirement(name = "bearerAuth")
public class LocationController {

    private final LocationService locationService;
    private final TrackingService trackingService;

    @PostMapping("/{id}/customer-location")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Customer shares their current GPS location for a booking")
    public ResponseEntity<ApiResponse<LocationResponse>> shareCustomerLocation(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody LocationRequest request) {

        LocationResponse location = locationService.saveCustomerLocation(id, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Location shared with owner", location));
    }

    @GetMapping("/{id}/customer-location")
    @PreAuthorize("hasAnyRole('OWNER', 'CUSTOMER')")
    @Operation(summary = "View the customer's shared location for a booking")
    public ResponseEntity<ApiResponse<LocationResponse>> getCustomerLocation(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        LocationResponse location = locationService.getCustomerLocation(id, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Location fetched", location));
    }

    @GetMapping("/{id}/tracking")
    @PreAuthorize("hasAnyRole('OWNER', 'CUSTOMER')")
    @Operation(summary = "Live tracking — resolves who to track (customer or delivery partner) " +
            "and their last known location, switching automatically per handover leg")
    public ResponseEntity<ApiResponse<TrackingResponse>> getTracking(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        TrackingResponse tracking = trackingService.getTracking(id, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Tracking info fetched", tracking));
    }
}
