package com.islamshariful.userservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PUT, so the body is the complete desired state: a field omitted is a field cleared.
 *
 * <p>No email field. Email belongs to auth-service and is only ever projected here from the token —
 * accepting one would let a caller desynchronise the two services through this endpoint.
 */
@Schema(description = "The complete profile the caller wants to end up with")
public record UpdateProfileRequest(
        @Schema(example = "Ada Lovelace") @Size(max = 150) String displayName,
        @Schema(example = "Mathematician. Writes about analytical engines.") @Size(max = 1000) String bio,
        @Schema(example = "https://cdn.example/avatars/ada.png") @Size(max = 2048) String avatarUrl,
        @Schema(example = "+8801700000000")
                @Size(max = 30)
                @Pattern(regexp = "^$|^\\+?[0-9 ()-]{5,30}$", message = "must look like a phone number")
                String phoneNumber,
        @Schema(description = "IANA zone id", example = "Asia/Dhaka") @Size(max = 64) String timeZone,
        @Schema(description = "BCP 47 language tag", example = "en-GB") @Size(max = 35) String locale) {}
