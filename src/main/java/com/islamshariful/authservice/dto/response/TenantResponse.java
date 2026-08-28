package com.islamshariful.authservice.dto.response;

import com.islamshariful.authservice.domain.Tenant;
import com.islamshariful.authservice.domain.TenantStatus;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String slug, String name, TenantStatus status, Instant createdAt) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.getStatus(), tenant.getCreatedAt());
    }
}
