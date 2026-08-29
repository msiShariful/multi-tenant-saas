package com.islamshariful.authservice.dto.request;

import com.islamshariful.authservice.domain.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

/** Replaces the user's roles wholesale — a PUT, so the body is the complete desired state. */
@Schema(description = "The complete set of roles the user should end up with")
public record UpdateUserRolesRequest(@Schema(example = "[\"TENANT_ADMIN\"]") @NotEmpty Set<RoleName> roles) {}
