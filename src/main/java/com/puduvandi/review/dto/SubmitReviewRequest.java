package com.puduvandi.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to rate a completed trip")
public record SubmitReviewRequest(

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @Schema(example = "5")
    Integer rating,

    @Size(max = 1000, message = "Comment must be at most 1000 characters")
    @Schema(example = "Great bike, smooth ride!")
    String comment

) {}
