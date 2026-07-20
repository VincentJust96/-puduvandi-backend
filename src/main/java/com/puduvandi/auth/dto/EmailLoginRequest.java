package com.puduvandi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to log in with email + password.
 */
@Schema(description = "Request to log in with email + password")
public record EmailLoginRequest(

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Schema(example = "ravi@example.com")
    String email,

    @NotBlank(message = "Password is required")
    @Schema(example = "a-strong-password")
    String password

) {}
