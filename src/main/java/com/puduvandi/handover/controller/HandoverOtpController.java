package com.puduvandi.handover.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.common.enums.HandoverPurpose;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.handover.dto.HandoverOtpResponse;
import com.puduvandi.handover.dto.HandoverVerifyRequest;
import com.puduvandi.handover.dto.HandoverVerifyResponse;
import com.puduvandi.handover.service.HandoverOtpService;
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

/**
 * OTP-gated bike handover: pickup and return, for both self-pickup and
 * partner-delivery bookings. The same two endpoints serve three different
 * roles (CUSTOMER/OWNER/PARTNER) depending on the {purpose} path segment —
 * HandoverOtpService enforces the precise per-purpose role+identity check.
 */
@RestController
@RequestMapping("/api/v1/bookings/{id}/handover/{purpose}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER','OWNER','PARTNER')")
@Tag(name = "Handover OTP", description = "In-app OTP verification for bike pickup/return handovers")
@SecurityRequirement(name = "bearerAuth")
public class HandoverOtpController {

    private final HandoverOtpService handoverOtpService;

    @PostMapping("/generate")
    @Operation(summary = "Generate a handover OTP (in-app display only — never sent via SMS/WhatsApp)")
    public ResponseEntity<ApiResponse<HandoverOtpResponse>> generate(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @PathVariable String purpose) {

        HandoverOtpResponse response = handoverOtpService.generate(id, resolvePurpose(purpose), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("OTP generated", response));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify a handover OTP and apply the resulting state transition")
    public ResponseEntity<ApiResponse<HandoverVerifyResponse>> verify(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @PathVariable String purpose,
            @Valid @RequestBody HandoverVerifyRequest request) {

        HandoverVerifyResponse response = handoverOtpService.verify(
                id, resolvePurpose(purpose), request.otp(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Handover verified", response));
    }

    private HandoverPurpose resolvePurpose(String purpose) {
        try {
            return HandoverPurpose.valueOf(purpose.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unknown handover purpose: " + purpose);
        }
    }
}
