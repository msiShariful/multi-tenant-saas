package com.islamshariful.authservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-service sign-up: provisions a tenant and its first administrator in one transaction. */
@Schema(description = "Creates a new tenant together with its first administrator account")
public record TenantRegistrationRequest(
        @Schema(example = "Acme Corporation")
                @NotBlank
                @Size(max = 150)
                String tenantName,
        @Schema(
                        description = "URL-safe tenant identifier, supplied at login. Lower-case letters, digits and hyphens.",
                        example = "acme")
                @NotBlank
                @Size(min = 3, max = 63)
                @Pattern(
                        regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
                        message = "must contain only lower-case letters, digits and hyphens, and may not start or end with a hyphen")
                String tenantSlug,
        @Schema(example = "admin@acme.example") @NotBlank @Email @Size(max = 320) String adminEmail,
        /*
         * Length only, no composition rules. NIST SP 800-63B recommends exactly this: mandated
         * mixes of character classes push users toward predictable substitutions without adding
         * real entropy, whereas a 12-character floor does.
         */
        @Schema(description = "At least 12 characters. No composition rules (NIST SP 800-63B).", example = "correct horse battery staple")
                @NotBlank
                @Size(min = 12, max = 128)
                String adminPassword,
        @Schema(example = "Ada") @Size(max = 100) String firstName,
        @Schema(example = "Lovelace") @Size(max = 100) String lastName) {}
