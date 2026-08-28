package com.islamshariful.authservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The tenant slug is part of the credential set because email is unique per tenant, not globally — the same
 * address may identify different people in different tenants.
 */
@Schema(description = "Tenant-scoped credentials")
public record LoginRequest(
        @Schema(example = "acme-corp") @NotBlank @Size(max = 63) String tenantSlug,
        @Schema(example = "admin@acme.example") @NotBlank @Email @Size(max = 320) String email,
        @Schema(example = "correct horse battery staple") @NotBlank @Size(max = 128) String password) {}
