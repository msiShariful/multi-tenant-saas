package com.islamshariful.userservice.dto.response;

import com.islamshariful.userservice.domain.UserProfile;
import java.time.Instant;
import java.util.UUID;

/**
 * The read model for a profile.
 *
 * @param userId the auth-service user id, which is also this profile's primary key
 */
public record ProfileResponse(
        UUID userId,
        UUID tenantId,
        String email,
        String displayName,
        String bio,
        String avatarUrl,
        String phoneNumber,
        String timeZone,
        String locale,
        Instant createdAt,
        Instant updatedAt) {

    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getTenantId(),
                profile.getEmail(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getAvatarUrl(),
                profile.getPhoneNumber(),
                profile.getTimeZone(),
                profile.getLocale(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
