package com.puduvandi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to update customer profile fields.
 */
@Schema(description = "Update user profile")
public record UpdateProfileRequest(

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(example = "Ravi Kumar")
    String fullName,

    @Email(message = "Invalid email address")
    @Schema(example = "ravi@gmail.com")
    String email,

    @Schema(example = "https://cdn.puduvandi.com/profiles/ravi.jpg")
    String profileImageUrl

) {}
