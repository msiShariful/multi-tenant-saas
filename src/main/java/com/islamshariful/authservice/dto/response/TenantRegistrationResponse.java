package com.islamshariful.authservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sign-up returns the created resources, not tokens: registration and authentication stay separate
 * operations, so the client logs in exactly the same way it will on every subsequent visit.
 */
@Schema(description = "The provisioned tenant and its administrator")
public record TenantRegistrationResponse(TenantResponse tenant, UserResponse administrator) {}
