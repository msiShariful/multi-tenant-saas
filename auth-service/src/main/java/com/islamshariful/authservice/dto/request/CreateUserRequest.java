package com.islamshariful.authservice.dto.request;

import com.islamshariful.authservice.domain.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Administrator-driven user provisioning. There is no {@code tenantId} field on purpose: the tenant comes
 * from the caller's token, so an admin cannot create a user in someone else's tenant by editing the payload.
 */
@Schema(description = "Creates a user inside the calling administrator's tenant")
public record CreateUserRequest(
        @Schema(example = "ada@acme.example") @NotBlank @Email @Size(max = 320) String email,
        @Schema(description = "At least 12 characters") @NotBlank @Size(min = 12, max = 128) String password,
        @Schema(example = "Ada") @Size(max = 100) String firstName,
        @Schema(example = "Lovelace") @Size(max = 100) String lastName,
        @Schema(example = "[\"TENANT_USER\"]") @NotEmpty Set<RoleName> roles) {}
