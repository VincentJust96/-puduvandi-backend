package com.puduvandi.push.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.config.PushProperties;
import com.puduvandi.push.dto.SubscribeRequest;
import com.puduvandi.push.service.WebPushService;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
@Tag(name = "Push", description = "Browser Web Push (VAPID) subscription management")
public class PushController {

    private final WebPushService webPushService;
    private final PushProperties pushProperties;

    @GetMapping("/vapid-public-key")
    @Operation(summary = "Get the VAPID public key needed to subscribe (public, no auth required)")
    public ResponseEntity<ApiResponse<String>> getVapidPublicKey() {
        return ResponseEntity.ok(ApiResponse.success("VAPID public key", pushProperties.getVapidPublicKey()));
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Register a browser push subscription for the logged-in user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> subscribe(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody SubscribeRequest request,
            HttpServletRequest httpRequest) {

        webPushService.subscribe(principal.getUserId(), request, httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Subscribed"));
    }

    @DeleteMapping("/subscribe")
    @Operation(summary = "Remove a browser push subscription")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestParam String endpoint) {
        webPushService.unsubscribe(endpoint);
        return ResponseEntity.ok(ApiResponse.success("Unsubscribed"));
    }
}
