package com.puduvandi.user.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.security.PuduvandiUserPrincipal;
import com.puduvandi.user.dto.PhoneChangeRequestResponse;
import com.puduvandi.user.dto.PhoneOtpRequest;
import com.puduvandi.user.dto.UpdateProfileRequest;
import com.puduvandi.user.dto.UploadDocumentRequest;
import com.puduvandi.user.dto.UserDocumentResponse;
import com.puduvandi.user.dto.UserProfileResponse;
import com.puduvandi.user.dto.VerifyPhoneOtpRequest;
import com.puduvandi.user.service.UserService;
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

/**
 * Customer profile management endpoints.
 * All endpoints require CUSTOMER role.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Customer profile and document management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        UserProfileResponse profile = userService.getProfile(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", profile));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update my profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserProfileResponse profile = userService.updateProfile(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", profile));
    }

    @PostMapping("/me/documents")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Upload driving license or document")
    public ResponseEntity<ApiResponse<UserDocumentResponse>> uploadDocument(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody UploadDocumentRequest request) {

        UserDocumentResponse document = userService.uploadDocument(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Document uploaded successfully", document));
    }

    @GetMapping("/me/documents")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get my uploaded documents")
    public ResponseEntity<ApiResponse<List<UserDocumentResponse>>> getMyDocuments(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        List<UserDocumentResponse> documents = userService.getMyDocuments(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Documents fetched successfully", documents));
    }

    // ===== Phone: add (no existing number — instant) =====

    @PostMapping("/me/phone/send-otp")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send OTP to add a first phone number", description = "Only for accounts with no phone number yet (e.g. email/password signups).")
    public ResponseEntity<ApiResponse<Void>> sendPhoneAddOtp(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody PhoneOtpRequest request) {

        userService.sendPhoneAddOtp(principal.getUserId(), request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully to " + request.phoneNumber()));
    }

    @PostMapping("/me/phone/verify-otp")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify OTP and add the phone number to my account")
    public ResponseEntity<ApiResponse<UserProfileResponse>> verifyPhoneAddOtp(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody VerifyPhoneOtpRequest request) {

        UserProfileResponse profile = userService.verifyPhoneAddOtp(principal.getUserId(), request.phoneNumber(), request.otp());
        return ResponseEntity.ok(ApiResponse.success("Phone number added", profile));
    }

    // ===== Phone: change (existing number — OTP proves ownership, then admin review) =====

    @PostMapping("/me/phone-change/send-otp")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send OTP to a new phone number before requesting a change")
    public ResponseEntity<ApiResponse<Void>> sendPhoneChangeOtp(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody PhoneOtpRequest request) {

        userService.sendPhoneChangeOtp(principal.getUserId(), request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully to " + request.phoneNumber()));
    }

    @PostMapping("/me/phone-change/verify-otp")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify OTP and file a phone-change request for admin review")
    public ResponseEntity<ApiResponse<PhoneChangeRequestResponse>> verifyPhoneChangeOtp(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody VerifyPhoneOtpRequest request) {

        PhoneChangeRequestResponse response = userService.verifyPhoneChangeOtp(principal.getUserId(), request.phoneNumber(), request.otp());
        return ResponseEntity.ok(ApiResponse.success("Change request submitted for admin review", response));
    }

    @GetMapping("/me/phone-change/pending")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my pending phone-change request, if any")
    public ResponseEntity<ApiResponse<PhoneChangeRequestResponse>> getMyPendingPhoneChangeRequest(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        PhoneChangeRequestResponse response = userService.getMyPendingPhoneChangeRequest(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Pending request fetched", response));
    }

    @DeleteMapping("/me/phone-change/pending")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel my pending phone-change request before admin reviews it")
    public ResponseEntity<ApiResponse<Void>> cancelMyPendingPhoneChangeRequest(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        userService.cancelMyPendingPhoneChangeRequest(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Request cancelled"));
    }
}
