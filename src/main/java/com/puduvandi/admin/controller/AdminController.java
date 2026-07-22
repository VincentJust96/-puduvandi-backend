package com.puduvandi.admin.controller;

import com.puduvandi.admin.dto.*;
import com.puduvandi.admin.service.AdminService;
import com.puduvandi.bike.dto.BikeResponse;
import com.puduvandi.bike.dto.UpdateBikeRequest;
import com.puduvandi.booking.dto.BookingResponse;
import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.common.enums.*;
import com.puduvandi.deposit.dto.DepositClaimResponse;
import com.puduvandi.deposit.dto.FailedRefundResponse;
import com.puduvandi.deposit.service.DepositClaimService;
import com.puduvandi.owner.dto.CompleteOwnerProfileRequest;
import com.puduvandi.owner.dto.OwnerProfileResponse;
import com.puduvandi.partner.dto.CompletePartnerProfileRequest;
import com.puduvandi.partner.dto.PartnerProfileResponse;
import com.puduvandi.security.PuduvandiUserPrincipal;
import com.puduvandi.user.dto.PhoneChangeRequestResponse;
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
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin", description = "Admin panel — KYC, bike approval, commission. User account data and all deletes live under /super-admin.")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final DepositClaimService depositClaimService;

    // ===== DASHBOARD =====

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<ApiResponse<AdminDashboardStats>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard stats", adminService.getDashboardStats()));
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

    // ===== DELIVERY PARTNER KYC =====

    @GetMapping("/partners")
    @Operation(summary = "List all delivery partners (paginated, filter by KYC status)")
    public ResponseEntity<ApiResponse<Page<AdminPartnerResponse>>> listPartners(
            @RequestParam(required = false) KycStatus kycStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success("Partners fetched", adminService.listPartners(kycStatus, page, size)));
    }

    @PatchMapping("/partners/{partnerId}/approve-kyc")
    @Operation(summary = "Approve delivery partner KYC")
    public ResponseEntity<ApiResponse<AdminPartnerResponse>> approvePartnerKyc(@PathVariable Long partnerId) {
        return ResponseEntity.ok(ApiResponse.success("KYC approved", adminService.approvePartnerKyc(partnerId)));
    }

    @PatchMapping("/partners/{partnerId}/reject-kyc")
    @Operation(summary = "Reject delivery partner KYC with a reason")
    public ResponseEntity<ApiResponse<AdminPartnerResponse>> rejectPartnerKyc(
            @PathVariable Long partnerId,
            @Valid @RequestBody RejectReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("KYC rejected", adminService.rejectPartnerKyc(partnerId, request.reason())));
    }

    @GetMapping("/partners/{partnerId}")
    @Operation(summary = "Get full delivery partner profile detail (for admin edit)")
    public ResponseEntity<ApiResponse<PartnerProfileResponse>> getPartnerDetail(@PathVariable Long partnerId) {
        return ResponseEntity.ok(ApiResponse.success("Partner detail fetched", adminService.getPartnerDetail(partnerId)));
    }

    @PutMapping("/partners/{partnerId}")
    @Operation(summary = "Edit delivery partner vehicle/bank details")
    public ResponseEntity<ApiResponse<AdminPartnerResponse>> updatePartner(
            @PathVariable Long partnerId,
            @Valid @RequestBody CompletePartnerProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Partner updated", adminService.updatePartner(partnerId, request)));
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

    // ===== DOCUMENTS (driving licence review) =====

    @GetMapping("/documents")
    @Operation(summary = "List uploaded documents (paginated, filter by type/status)")
    public ResponseEntity<ApiResponse<Page<AdminUserDocumentResponse>>> listDocuments(
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AdminUserDocumentResponse> documents = adminService.listDocuments(documentType, status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Documents fetched", documents));
    }

    @PatchMapping("/documents/{documentId}/approve")
    @Operation(summary = "Approve an uploaded document")
    public ResponseEntity<ApiResponse<AdminUserDocumentResponse>> approveDocument(@PathVariable Long documentId) {
        return ResponseEntity.ok(ApiResponse.success("Document approved", adminService.approveDocument(documentId)));
    }

    @PatchMapping("/documents/{documentId}/reject")
    @Operation(summary = "Reject an uploaded document with a reason")
    public ResponseEntity<ApiResponse<AdminUserDocumentResponse>> rejectDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody RejectReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Document rejected", adminService.rejectDocument(documentId, request.reason())));
    }

    // ===== PHONE CHANGE REQUESTS =====

    @GetMapping("/phone-change-requests")
    @Operation(summary = "List phone-change requests (paginated, filter by status)")
    public ResponseEntity<ApiResponse<Page<PhoneChangeRequestResponse>>> listPhoneChangeRequests(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<PhoneChangeRequestResponse> requests = adminService.listPhoneChangeRequests(status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Phone change requests fetched", requests));
    }

    @PatchMapping("/phone-change-requests/{requestId}/approve")
    @Operation(summary = "Approve a phone-change request")
    public ResponseEntity<ApiResponse<PhoneChangeRequestResponse>> approvePhoneChangeRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("Phone change approved", adminService.approvePhoneChangeRequest(requestId)));
    }

    @PatchMapping("/phone-change-requests/{requestId}/reject")
    @Operation(summary = "Reject a phone-change request with a reason")
    public ResponseEntity<ApiResponse<PhoneChangeRequestResponse>> rejectPhoneChangeRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody RejectReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Phone change rejected", adminService.rejectPhoneChangeRequest(requestId, request.reason())));
    }

    // ===== DEPOSIT CLAIMS =====

    @GetMapping("/deposit-claims")
    @Operation(summary = "List security-deposit deduction claims (paginated, filter by status)")
    public ResponseEntity<ApiResponse<Page<DepositClaimResponse>>> listDepositClaims(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success("Deposit claims fetched", depositClaimService.listClaims(status, page, size)));
    }

    @PatchMapping("/deposit-claims/{claimId}/approve")
    @Operation(summary = "Approve a deposit claim as filed — deducts the claimed amount and refunds the rest")
    public ResponseEntity<ApiResponse<DepositClaimResponse>> approveDepositClaim(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long claimId) {

        return ResponseEntity.ok(ApiResponse.success("Deposit claim approved",
                depositClaimService.approveClaim(principal.getUserId(), claimId)));
    }

    @PatchMapping("/deposit-claims/{claimId}/reject")
    @Operation(summary = "Reject a deposit claim with a reason — the customer's full deposit is refunded")
    public ResponseEntity<ApiResponse<DepositClaimResponse>> rejectDepositClaim(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long claimId,
            @Valid @RequestBody RejectReasonRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Deposit claim rejected",
                depositClaimService.rejectClaim(principal.getUserId(), claimId, request.reason())));
    }

    @GetMapping("/deposit-claims/failed-refunds")
    @Operation(summary = "List bookings whose deposit refund failed against Razorpay (paginated)")
    public ResponseEntity<ApiResponse<Page<FailedRefundResponse>>> listFailedRefunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success("Failed refunds fetched", depositClaimService.listFailedRefunds(page, size)));
    }

    @PostMapping("/deposit-claims/failed-refunds/{bookingId}/retry")
    @Operation(summary = "Retry a previously-failed deposit refund")
    public ResponseEntity<ApiResponse<Void>> retryFailedRefund(@PathVariable Long bookingId) {
        depositClaimService.retryFailedRefund(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Refund retried — check the booking's deposit status for the outcome"));
    }
}
