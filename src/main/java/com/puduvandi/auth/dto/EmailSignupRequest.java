package com.puduvandi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create an account with email + password.
 * Role is not required yet — new users pick it after signup, same as the
 * phone/OTP flow (/auth/set-role).
 */
@Schema(description = "Request to sign up with email + password")
public record EmailSignupRequest(

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Schema(example = "ravi@example.com")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(example = "a-strong-password")
    String password

) {}
