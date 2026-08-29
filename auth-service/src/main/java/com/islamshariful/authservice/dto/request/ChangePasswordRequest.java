package com.islamshariful.authservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Changes the authenticated caller's own password")
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @Schema(description = "At least 12 characters") @NotBlank @Size(min = 12, max = 128) String newPassword) {}
