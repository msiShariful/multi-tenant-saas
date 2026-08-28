package com.islamshariful.authservice.dto.response;

import com.islamshariful.authservice.domain.RoleName;
import com.islamshariful.authservice.domain.User;
import com.islamshariful.authservice.domain.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The read model for a user. Notably absent: {@code passwordHash}, {@code failedLoginAttempts} and
 * {@code lockedUntil}. Serialising the entity directly would have leaked all three — the reason entities
 * never cross the controller boundary.
 */
public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Set<RoleName> roles,
        Instant createdAt,
        Instant lastLoginAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                user.roleNames(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
