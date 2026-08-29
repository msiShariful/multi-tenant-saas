package com.islamshariful.userservice.web;

import com.islamshariful.userservice.dto.request.UpdateProfileRequest;
import com.islamshariful.userservice.dto.response.PageResponse;
import com.islamshariful.userservice.dto.response.ProfileResponse;
import com.islamshariful.userservice.security.AuthenticatedUser;
import com.islamshariful.userservice.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped user profiles.
 *
 * <p>Nothing here takes a tenant id. The tenant comes from the caller's validated token and is applied by
 * the persistence layer, so an endpoint cannot be pointed at another tenant's data by editing a payload
 * or a path.
 */
@RestController
@RequestMapping(path = "/api/v1/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Profiles", description = "Tenant-scoped user profiles")
public class ProfileController {

    private final UserProfileService userProfileService;

    public ProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    @Operation(
            summary = "The caller's own profile, created on first access",
            description = """
                    Provisioned just in time. Users are created in auth-service, so the first time someone
                    reaches this service there is no row yet — one is materialised from the token's claims,
                    which auth-service signed and this service verified against its published key.

                    Always 200, never 404: a valid token means the user exists.""")
    @ApiResponse(
            responseCode = "200",
            description = "The profile, existing or freshly provisioned",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfileResponse.class)))
    public ProfileResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return userProfileService.getOrCreateOwnProfile(caller);
    }

    @PutMapping("/me")
    @Operation(
            summary = "Replace the caller's own profile",
            description = "PUT, so the body is the complete desired state — an omitted field is cleared. "
                    + "Provisions the profile first if this is the caller's first request.")
    @ApiResponse(
            responseCode = "200",
            description = "The updated profile",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfileResponse.class)))
    public ProfileResponse updateMe(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody UpdateProfileRequest request) {
        return userProfileService.updateOwnProfile(caller, request);
    }

    @GetMapping
    @Operation(
            summary = "The tenant directory",
            description = "Every member of the caller's tenant, and nobody else. Optional `search` matches "
                    + "email or display name.")
    public PageResponse<ProfileResponse> list(
            @Parameter(description = "Matches email or display name") @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return userProfileService.list(search, pageable);
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "One profile from the caller's tenant",
            description = "A user id from another tenant returns 404, not 403 — from this tenant's point of "
                    + "view the row genuinely does not exist, and 403 would confirm that it does.")
    @ApiResponse(
            responseCode = "200",
            description = "The profile",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfileResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such profile in this tenant", content = @Content)
    public ProfileResponse get(@PathVariable UUID userId) {
        return userProfileService.get(userId);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(
            summary = "Delete a profile",
            description = "Removes profile data only. The account itself lives in auth-service and is "
                    + "untouched, so the user can still sign in and would be provisioned a blank profile.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such profile in this tenant", content = @Content)
    public ResponseEntity<Void> delete(@PathVariable UUID userId) {
        userProfileService.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
