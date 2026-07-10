package com.puduvandi.admin.controller;

import com.puduvandi.admin.dto.*;
import com.puduvandi.admin.service.AdminService;
import com.puduvandi.bike.dto.BikeResponse;
import com.puduvandi.bike.dto.UpdateBikeRequest;
import com.puduvandi.booking.dto.BookingResponse;
import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.common.enums.*;
import com.puduvandi.delivery.dto.DeliveryRateResponse;
import com.puduvandi.delivery.dto.UpdateDeliveryRateRequest;
import com.puduvandi.owner.dto.CompleteOwnerProfileRequest;
import com.puduvandi.owner.dto.OwnerProfileResponse;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin panel — user management, KYC, bike approval, commission")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    // ===== DASHBOARD =====

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<ApiResponse<AdminDashboardStats>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard stats", adminService.getDashboardStats()));
    }

    // ===== USERS =====

    @GetMapping("/users")
    @Operation(summary = "List all users (paginated, filter by role/status)")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AdminUserResponse> users = adminService.listUsers(role, status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Users fetched", users));
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Soft-delete a user account (preserves booking history)")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }

    @PatchMapping("/users/{userId}/suspend")
    @Operation(summary = "Suspend a user account")
    public ResponseEntity<ApiResponse<AdminUserResponse>> suspendUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User suspended", adminService.suspendUser(userId)));
    }

    @PatchMapping("/users/{userId}/unsuspend")
    @Operation(summary = "Unsuspend a user account")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unsuspendUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User unsuspended", adminService.unsuspendUser(userId)));
    }

    @PutMapping("/users/{userId}")
    @Operation(summary = "Edit a user's basic profile fields")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User updated", adminService.updateUser(userId, request)));
    }

    // ===== OWNER KYC =====

    @GetMapping("/owners")
    @Operation(summary = "List all owners (paginated, filter by KYC status)")
    public ResponseEntity<ApiResponse<Page<AdminOwnerResponse>>> listOwners(
            @RequestParam(required = false) KycStatus kycStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AdminOwnerResponse> owners = adminService.listOwners(kycStatus, page, size);
        return ResponseEntity.ok(ApiResponse.success("Owners fetched", owners));
    }

    @PatchMapping("/owners/{ownerId}/approve-kyc")
    @Operation(summary = "Approve owner KYC")
    public ResponseEntity<ApiResponse<AdminOwnerResponse>> approveKyc(@PathVariable Long ownerId) {
        return ResponseEntity.ok(ApiResponse.success("KYC approved", adminService.approveOwnerKyc(ownerId)));
    }

    @PatchMapping("/owners/{ownerId}/reject-kyc")
    @Operation(summary = "Reject owner KYC with a reason")
    public ResponseEntity<ApiResponse<AdminOwnerResponse>> rejectKyc(
            @PathVariable Long ownerId,
            @Valid @RequestBody RejectReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("KYC rejected", adminService.rejectOwnerKyc(ownerId, request.reason())));
    }

    @GetMapping("/owners/{ownerId}")
    @Operation(summary = "Get full owner profile detail (for admin edit)")
    public ResponseEntity<ApiResponse<OwnerProfileResponse>> getOwnerDetail(@PathVariable Long ownerId) {
        return ResponseEntity.ok(ApiResponse.success("Owner detail fetched", adminService.getOwnerDetail(ownerId)));
    }

    @PutMapping("/owners/{ownerId}")
    @Operation(summary = "Edit owner business/bank details")
    public ResponseEntity<ApiResponse<AdminOwnerResponse>> updateOwner(
            @PathVariable Long ownerId,
            @Valid @RequestBody CompleteOwnerProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Owner updated", adminService.updateOwner(ownerId, request)));
    }

    @DeleteMapping("/owners/{ownerId}")
    @Operation(summary = "Soft-delete an owner profile (cascades to their bikes)")
    public ResponseEntity<ApiResponse<Void>> deleteOwner(@PathVariable Long ownerId) {
        adminService.deleteOwner(ownerId);
        return ResponseEntity.ok(ApiResponse.success("Owner deleted", null));
    }

    // ===== BIKES =====

    @GetMapping("/bikes")
    @Operation(summary = "List all bikes (paginated, filter by verification status)")
    public ResponseEntity<ApiResponse<Page<BikeResponse>>> listBikes(
            @RequestParam(required = false) BikeVerificationStatus verificationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<BikeResponse> bikes = adminService.listBikes(verificationStatus, page, size);
        return ResponseEntity.ok(ApiResponse.success("Bikes fetched", bikes));
    }

    @PatchMapping("/bikes/{bikeId}/approve")
    @Operation(summary = "Approve a bike listing")
    public ResponseEntity<ApiResponse<BikeResponse>> approveBike(@PathVariable Long bikeId) {
        return ResponseEntity.ok(ApiResponse.success("Bike approved", adminService.approveBike(bikeId)));
    }

    @PatchMapping("/bikes/{bikeId}/reject")
    @Operation(summary = "Reject a bike listing with a reason")
    public ResponseEntity<ApiResponse<BikeResponse>> rejectBike(
            @PathVariable Long bikeId,
            @Valid @RequestBody RejectReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Bike rejected", adminService.rejectBike(bikeId, request.reason())));
    }

    @PutMapping("/bikes/{bikeId}")
    @Operation(summary = "Edit bike listing details")
    public ResponseEntity<ApiResponse<BikeResponse>> updateBike(
            @PathVariable Long bikeId,
            @Valid @RequestBody UpdateBikeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Bike updated", adminService.updateBike(bikeId, request)));
    }

    @DeleteMapping("/bikes/{bikeId}")
    @Operation(summary = "Soft-delete a bike listing")
    public ResponseEntity<ApiResponse<Void>> deleteBike(@PathVariable Long bikeId) {
        adminService.deleteBike(bikeId);
        return ResponseEntity.ok(ApiResponse.success("Bike deleted", null));
    }

    // ===== BOOKINGS =====

    @GetMapping("/bookings")
    @Operation(summary = "List all bookings (paginated, filter by status)")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> listBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<BookingResponse> bookings = adminService.listBookings(status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Bookings fetched", bookings));
    }

    // ===== COMMISSION =====

    @GetMapping("/commission")
    @Operation(summary = "Get current commission settings")
    public ResponseEntity<ApiResponse<CommissionResponse>> getCommission() {
        return ResponseEntity.ok(ApiResponse.success("Commission fetched", adminService.getActiveCommission()));
    }

    @PutMapping("/commission")
    @Operation(summary = "Update platform commission percentage")
    public ResponseEntity<ApiResponse<CommissionResponse>> updateCommission(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody UpdateCommissionRequest request) {

        CommissionResponse response = adminService.updateCommission(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Commission updated to " + request.commissionPercent() + "%", response));
    }

    // ===== DELIVERY RATE =====

    @GetMapping("/delivery-rate")
    @Operation(summary = "Get current partner delivery rate settings")
    public ResponseEntity<ApiResponse<DeliveryRateResponse>> getDeliveryRate() {
        return ResponseEntity.ok(ApiResponse.success("Delivery rate fetched", adminService.getActiveDeliveryRate()));
    }

    @PutMapping("/delivery-rate")
    @Operation(summary = "Update the platform's per-km partner delivery rate")
    public ResponseEntity<ApiResponse<DeliveryRateResponse>> updateDeliveryRate(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody UpdateDeliveryRateRequest request) {

        DeliveryRateResponse response = adminService.updateDeliveryRate(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Delivery rate updated to ₹" + request.ratePerKm() + "/km", response));
    }
}
