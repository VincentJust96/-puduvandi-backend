package com.puduvandi.tripexpense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to set a trip's dashboard hero banner image, from a URL already returned by /files/upload")
public record SetTripCoverImageRequest(

    @NotBlank(message = "Image URL is required")
    String imageUrl

) {}
