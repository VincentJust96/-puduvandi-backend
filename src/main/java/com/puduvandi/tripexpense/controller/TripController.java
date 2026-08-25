package com.puduvandi.tripexpense.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.security.PuduvandiUserPrincipal;
import com.puduvandi.tripexpense.dto.AddMemberRequest;
import com.puduvandi.tripexpense.dto.CreateExpenseRequest;
import com.puduvandi.tripexpense.dto.CreateTripRequest;
import com.puduvandi.tripexpense.dto.SetTripCoverImageRequest;
import com.puduvandi.tripexpense.dto.SetTripCoverPositionRequest;
import com.puduvandi.tripexpense.dto.TripDashboardResponse;
import com.puduvandi.tripexpense.dto.TripJoinResponse;
import com.puduvandi.tripexpense.dto.TripSummaryResponse;
import com.puduvandi.tripexpense.dto.UpdateMemberContributionRequest;
import com.puduvandi.tripexpense.service.TripExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TripSplit endpoints. No role gate beyond {@code authenticated()} (see
 * SecurityConfig) — any logged-in user, regardless of CUSTOMER/OWNER/etc,
 * can use this feature.
 */
@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Tag(name = "TripSplit", description = "Trip expense tracking and splitting")
@SecurityRequirement(name = "bearerAuth")
public class TripController {

    private final TripExpenseService tripExpenseService;

    @GetMapping
    @Operation(summary = "List trips the current user is a member of")
    public ResponseEntity<ApiResponse<List<TripSummaryResponse>>> getMyTrips(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {
        List<TripSummaryResponse> trips = tripExpenseService.getMyTrips(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Trips fetched", trips));
    }

    @PostMapping
    @Operation(summary = "Create a trip — the caller becomes its first (creator) member")
    public ResponseEntity<ApiResponse<TripSummaryResponse>> createTrip(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody CreateTripRequest request) {
        TripSummaryResponse trip = tripExpenseService.createTrip(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Trip created", trip));
    }

    @PostMapping("/join/{token}")
    @Operation(summary = "Join a trip via its shareable invite link")
    public ResponseEntity<ApiResponse<TripJoinResponse>> joinTrip(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable String token) {
        TripJoinResponse trip = tripExpenseService.joinTripByToken(principal.getUserId(), token);
        return ResponseEntity.ok(ApiResponse.success("Joined trip", trip));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Permanently delete a trip (creator only) — cascades to its members and expenses")
    public ResponseEntity<ApiResponse<Void>> deleteTrip(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {
        tripExpenseService.deleteTrip(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Trip deleted"));
    }

    @PutMapping("/{id}/cover-image")
    @Operation(summary = "Set the dashboard hero banner image, from a URL already returned by /files/upload")
    public ResponseEntity<ApiResponse<Void>> setCoverImage(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SetTripCoverImageRequest request) {
        tripExpenseService.setCoverImage(principal.getUserId(), id, request.imageUrl());
        return ResponseEntity.ok(ApiResponse.success("Cover image updated"));
    }

    @PutMapping("/{id}/cover-image/position")
    @Operation(summary = "Reposition the cover image's vertical focal point (0-100, 50 = center)")
    public ResponseEntity<ApiResponse<Void>> setCoverImagePosition(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SetTripCoverPositionRequest request) {
        tripExpenseService.setCoverImagePosition(principal.getUserId(), id, request.positionY());
        return ResponseEntity.ok(ApiResponse.success("Cover image position updated"));
    }

    @DeleteMapping("/{id}/cover-image")
    @Operation(summary = "Remove the dashboard hero banner image, reverting to the default gradient")
    public ResponseEntity<ApiResponse<Void>> clearCoverImage(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {
        tripExpenseService.clearCoverImage(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Cover image removed"));
    }

    @GetMapping("/{id}/dashboard")
    @Operation(summary = "Full trip dashboard — budget, balances, expenses, chart data, map")
    public ResponseEntity<ApiResponse<TripDashboardResponse>> getDashboard(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id) {
        TripDashboardResponse dashboard = tripExpenseService.getDashboard(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Dashboard fetched", dashboard));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Invite a traveler by phone number")
    public ResponseEntity<ApiResponse<Void>> addMember(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AddMemberRequest request) {
        tripExpenseService.addMember(principal.getUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Traveler added"));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @Operation(summary = "Remove a traveler (creator only, or leaving yourself) — blocked once they have expense history")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long memberId) {
        tripExpenseService.removeMember(principal.getUserId(), id, memberId);
        return ResponseEntity.ok(ApiResponse.success("Traveler removed"));
    }

    @PutMapping("/{id}/members/{memberId}/contribution")
    @Operation(summary = "Update how much a traveler has contributed toward the trip budget so far (creator only)")
    public ResponseEntity<ApiResponse<Void>> updateMemberContribution(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberContributionRequest request) {
        tripExpenseService.updateMemberContribution(principal.getUserId(), id, memberId, request.contributedAmount());
        return ResponseEntity.ok(ApiResponse.success("Contribution updated"));
    }

    @PostMapping("/{id}/expenses")
    @Operation(summary = "Log an expense, split equally across the given members (defaults to everyone)")
    public ResponseEntity<ApiResponse<Void>> addExpense(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CreateExpenseRequest request) {
        tripExpenseService.addExpense(principal.getUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Expense added"));
    }

    @DeleteMapping("/{id}/expenses/{expenseId}")
    @Operation(summary = "Delete a logged expense (the person who paid for it, or the trip creator, only)")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long expenseId) {
        tripExpenseService.deleteExpense(principal.getUserId(), id, expenseId);
        return ResponseEntity.ok(ApiResponse.success("Expense deleted"));
    }
}
