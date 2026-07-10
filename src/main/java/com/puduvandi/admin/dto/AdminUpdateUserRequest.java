package com.puduvandi.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Admin edit of a user's basic profile fields")
public record AdminUpdateUserRequest(

    @NotBlank(message = "Full name is required")
    @Schema(example = "Ravi Kumar")
    String fullName,

    @Email(message = "Invalid email format")
    @Schema(example = "ravi@example.com")
    String email

) {}
