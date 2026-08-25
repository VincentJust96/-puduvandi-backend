package com.puduvandi.common.controller;

import com.puduvandi.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately does not touch the database. This is what an external
 * scheduler (see .github/workflows/keep-alive.yml) pings every few minutes
 * to stop Render's free-tier container from idling out — hitting a
 * DB-backed endpoint instead would keep the Neon compute permanently
 * "active" and burn its free-tier compute-hour quota the same way
 * minimum-idle: 5 did (see TripExpenseService git history / application.yml).
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Liveness check for keep-alive pings (public, no auth, no DB access)")
public class HealthController {

    @GetMapping
    @Operation(summary = "Liveness check (public, no auth required)")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }
}
