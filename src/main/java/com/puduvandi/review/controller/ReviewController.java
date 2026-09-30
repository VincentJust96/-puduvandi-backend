package com.puduvandi.review.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.review.dto.ReviewResponse;
import com.puduvandi.review.dto.SubmitReviewRequest;
import com.puduvandi.review.service.ReviewService;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Customer trip ratings")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/{bookingId}/review")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Rate a completed trip (one review per booking)")
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @PathVariable Long bookingId,
            @Valid @RequestBody SubmitReviewRequest request) {

        ReviewResponse review = reviewService.submitReview(principal.getUserId(), bookingId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thanks for rating your trip!", review));
    }
}
