package com.puduvandi.owner.controller;

import com.puduvandi.bike.dto.AddBikeRequest;
import com.puduvandi.bike.dto.BikeResponse;
import com.puduvandi.bike.dto.UpdateBikeRequest;
import com.puduvandi.bike.service.BikeService;
import com.puduvandi.booking.dto.BookingResponse;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.deposit.dto.DepositClaimResponse;
import com.puduvandi.deposit.dto.FileDepositClaimRequest;
import com.puduvandi.deposit.service.DepositClaimService;
import com.puduvandi.owner.dto.*;
import com.puduvandi.owner.service.OwnerService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
@Tag(name = "Owner", description = "Owner profile, bike management, bookings and dashboard")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('OWNER')")
public class OwnerController {

    private final OwnerService ownerService;
    private final BikeService bikeService;
    private final BookingService bookingService;
    private final DepositClaimService depositClaimService;

    // ===== PROFILE =====

    @GetMapping("/me")
    @Operation(summary = "Get my owner profile")
    public ResponseEntity<ApiResponse<OwnerProfileResponse>> getMyProfile(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        OwnerProfileResponse profile = ownerService.getProfile(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Owner profile fetched", profile));
    }

    @PostMapping("/me/complete-profile")
    @Operation(summary = "Complete KYC profile (business + bank details)")
    public ResponseEntity<ApiResponse<OwnerProfileResponse>> completeProfile(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody CompleteOwnerProfileRequest request) {

        OwnerProfileResponse profile = ownerService.completeProfile(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Owner profile updated", profile));
    }

    @PostMapping("/me/documents")
    @Operation(summary = "Upload a KYC document")
    public ResponseEntity<ApiResponse<OwnerDocumentResponse>> uploadDocument(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody UploadOwnerDocumentRequest request) {

        OwnerDocumentResponse doc = ownerService.uploadDocument(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Document uploaded", doc));
    }

    @GetMapping("/me/documents")
    @Operation(summary = "Get my uploaded KYC documents")
    public ResponseEntity<ApiResponse<List<OwnerDocumentResponse>>> getMyDocuments(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        List<OwnerDocumentResponse> docs = ownerService.getMyDocuments(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Documents fetched", docs));
    }

    // ===== DASHBOARD =====

    @GetMapping("/dashboard")
    @Operation(summary = "Owner dashboard — bike count, booking stats, total earnings")
    public ResponseEntity<ApiResponse<OwnerDashboardResponse>> getOwnerDashboard(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        OwnerDashboardResponse dashboard = ownerService.getOwnerDashboard(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Dashboard fetched", dashboard));
    }

    // ===== BIKE MANAGEMENT =====

    @GetMapping("/bikes")
    @Operation(summary = "List all my bike listings")
    public ResponseEntity<ApiResponse<Page<BikeResponse>>> getOwnerBikes(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<BikeResponse> bikes = bikeService.getMyBikes(principal.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Bikes fetched", bikes));
    }

    @PostMapping("/bikes")
    @Operation(summary = "Add a new bike listing")
    public ResponseEntity<ApiResponse<BikeResponse>> addBike(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody AddBikeRequest request) {

        BikeResponse bike = bikeService.addBike(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bike added. Pending admin approval.", bike));
    }

    @PutMapping("/bikes/{id}")
    @Operation(summary = "Update a bike listing")
    public ResponseEntity<ApiResponse<BikeResponse>> updateBike(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateBikeRequest request) {

        BikeResponse bike = bikeService.updateBike(principal.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Bike updated", bike));
    }

    @DeleteMapping("/bikes/{id}")
    @Operation(summary = "Delete a bike listing")
    public ResponseEntity<ApiResponse<Void>> deleteBike(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        bikeService.deleteBike(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Bike deleted"));
    }

    @PatchMapping("/bikes/{id}/availability")
    @Operation(summary = "Toggle bike availability (AVAILABLE ↔ UNAVAILABLE)")
    public ResponseEntity<ApiResponse<BikeResponse>> toggleAvailability(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {

        BikeResponse bike = bikeService.toggleAvailability(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Bike availability updated", bike));
    }

    // ===== BOOKINGS =====

    @GetMapping("/bookings")
    @Operation(summary = "Get all bookings for my bikes")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getOwnerBookings(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<BookingResponse> bookings = bookingService.getOwnerBookings(principal.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Bookings fetched", bookings));
    }

    @PostMapping("/bookings/{bookingId}/deposit-claim")
    @Operation(summary = "File a deduction claim against a completed booking's security deposit")
    public ResponseEntity<ApiResponse<DepositClaimResponse>> fileDepositClaim(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long bookingId,
            @Valid @RequestBody FileDepositClaimRequest request) {

        DepositClaimResponse response = depositClaimService.fileClaim(principal.getUserId(), bookingId, request);
        return ResponseEntity.ok(ApiResponse.success("Deposit claim filed", response));
    }
}
