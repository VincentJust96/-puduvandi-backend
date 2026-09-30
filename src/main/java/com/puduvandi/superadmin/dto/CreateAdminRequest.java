package com.puduvandi.superadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Create a new ADMIN account. Role is always ADMIN — only the super admin account itself can hold SUPER_ADMIN.")
public record CreateAdminRequest(
    @NotBlank(message = "Full name is required")
    String fullName,

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    String phoneNumber,

    @Email(message = "Invalid email")
    String email
) {}
