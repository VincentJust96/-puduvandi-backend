package com.puduvandi.auth.controller;

import com.puduvandi.auth.dto.*;
import com.puduvandi.auth.service.AuthService;
import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Phone OTP-based login and token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP", description = "Sends a 6-digit OTP. Role not required — new users pick it after verification.")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully to " + request.phoneNumber()));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP and login", description = "Returns tokens + isNewUser flag. If isNewUser=true, call /set-role next.")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthTokenResponse tokens = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", tokens));
    }

    @PostMapping("/set-role")
    @Operation(summary = "Set role for new user", description = "Called once after first login to pick CUSTOMER or OWNER. Requires bearer token from verify-otp.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> setRole(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody SetRoleRequest request) {

        AuthTokenResponse response = authService.setUserRole(principal.getUserId(), request.role());
        return ResponseEntity.ok(ApiResponse.success("Role set successfully", response));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthTokenResponse tokens = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", tokens));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — revokes all refresh tokens", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal PuduvandiUserPrincipal principal) {
        authService.logout(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }
}
