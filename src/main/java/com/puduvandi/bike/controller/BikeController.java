package com.puduvandi.bike.controller;

import com.puduvandi.bike.dto.AddBikeRequest;
import com.puduvandi.bike.dto.BikeResponse;
import com.puduvandi.bike.dto.UpdateBikeRequest;
import com.puduvandi.bike.service.BikeService;
import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Bike endpoints:
 * - Public: browse, search, get details
 * - Owner: add, edit, delete, toggle availability
 */
@RestController
@RequestMapping("/api/v1/bikes")
@RequiredArgsConstructor
@Tag(name = "Bikes", description = "Bike listing and management")
public class BikeController {

    private final BikeService bikeService;

    // ===== PUBLIC ENDPOINTS (No auth required) =====

    /**
     * GET /api/v1/bikes
     * Browse all available bikes with optional filters.
     */
    @GetMapping
    @Operation(summary = "Browse available bikes", description = "Filter by brand, model, area, fuel type, price range etc.")
    public ResponseEntity<ApiResponse<Page<BikeResponse>>> browseAvailableBikes(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) FuelType fuelType,
            @RequestParam(required = false) TransmissionType transmission,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean helmetIncluded,
            // Free-text search across brand/model/area (OR-matched) — separate from
            // the field-specific params above, which stay exact/single-field filters.
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<BikeResponse> bikes = bikeService.browseAvailableBikes(
                brand, model, area, fuelType, transmission, minPrice, maxPrice, helmetIncluded, search, page, size);
        return ResponseEntity.ok(ApiResponse.success("Bikes fetched successfully", bikes));
    }

    /**
     * GET /api/v1/bikes/{id}
     * Get full details of a specific approved bike.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get bike details")
    public ResponseEntity<ApiResponse<BikeResponse>> getBikeDetails(@PathVariable Long id) {
        BikeResponse bike = bikeService.getBikeDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Bike details fetched", bike));
    }

    // ===== OWNER ENDPOINTS =====

    /**
     * POST /api/v1/bikes
     * Owner adds a new bike listing.
     */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Add a new bike listing")
    public ResponseEntity<ApiResponse<BikeResponse>> addBike(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody AddBikeRequest request) {

        BikeResponse bike = bikeService.addBike(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bike added successfully. Pending admin approval.", bike));
    }

    /**
     * PUT /api/v1/bikes/{id}
     * Owner updates their bike listing.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update bike listing")
    public ResponseEntity<ApiResponse<BikeResponse>> updateBike(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateBikeRequest request) {

        BikeResponse bike = bikeService.updateBike(principal.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Bike updated successfully", bike));
    }

    /**
     * DELETE /api/v1/bikes/{id}
     * Owner soft-deletes their bike listing.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete bike listing")
    public ResponseEntity<ApiResponse<Void>> deleteBike(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        bikeService.deleteBike(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Bike deleted successfully"));
    }

    /**
     * PATCH /api/v1/bikes/{id}/toggle-availability
     * Owner marks bike as AVAILABLE or UNAVAILABLE.
     */
    @PatchMapping("/{id}/toggle-availability")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Toggle bike availability (AVAILABLE ↔ UNAVAILABLE)")
    public ResponseEntity<ApiResponse<BikeResponse>> toggleAvailability(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        BikeResponse bike = bikeService.toggleAvailability(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Bike availability updated", bike));
    }

    /**
     * GET /api/v1/bikes/my-bikes
     * Owner views all their own bike listings.
     */
    @GetMapping("/my-bikes")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get my bike listings")
    public ResponseEntity<ApiResponse<Page<BikeResponse>>> getMyBikes(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<BikeResponse> bikes = bikeService.getMyBikes(principal.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("My bikes fetched successfully", bikes));
    }
}
