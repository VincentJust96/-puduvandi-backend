package com.puduvandi.partner.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.partner.dto.*;
import com.puduvandi.partner.service.PartnerProfileService;
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
@RequestMapping("/api/v1/partner")
@RequiredArgsConstructor
@Tag(name = "Partner Profile", description = "Delivery partner profile, KYC documents and dashboard")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PARTNER')")
public class PartnerProfileController {

    private final PartnerProfileService partnerProfileService;

    @GetMapping("/me")
    @Operation(summary = "Get my delivery partner profile")
    public ResponseEntity<ApiResponse<PartnerProfileResponse>> getMyProfile(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success("Partner profile fetched",
                partnerProfileService.getProfile(principal.getUserId())));
    }

    @PostMapping("/me/complete-profile")
    @Operation(summary = "Complete KYC profile (vehicle + bank details)")
    public ResponseEntity<ApiResponse<PartnerProfileResponse>> completeProfile(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody CompletePartnerProfileRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Partner profile updated",
                partnerProfileService.completeProfile(principal.getUserId(), request)));
    }

    @PostMapping("/me/documents")
    @Operation(summary = "Upload a KYC document")
    public ResponseEntity<ApiResponse<PartnerDocumentResponse>> uploadDocument(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody UploadPartnerDocumentRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Document uploaded",
                partnerProfileService.uploadDocument(principal.getUserId(), request)));
    }

    @GetMapping("/me/documents")
    @Operation(summary = "Get my uploaded KYC documents")
    public ResponseEntity<ApiResponse<List<PartnerDocumentResponse>>> getMyDocuments(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success("Documents fetched",
                partnerProfileService.getMyDocuments(principal.getUserId())));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Partner dashboard — KYC status + delivery count")
    public ResponseEntity<ApiResponse<PartnerDashboardResponse>> getDashboard(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success("Dashboard fetched",
                partnerProfileService.getDashboard(principal.getUserId())));
    }
}
