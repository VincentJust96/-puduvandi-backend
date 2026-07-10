package com.puduvandi.user.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.security.PuduvandiUserPrincipal;
import com.puduvandi.user.dto.UpdateProfileRequest;
import com.puduvandi.user.dto.UploadDocumentRequest;
import com.puduvandi.user.dto.UserDocumentResponse;
import com.puduvandi.user.dto.UserProfileResponse;
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
}
