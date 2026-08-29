package com.islamshariful.authservice.web;

import com.islamshariful.authservice.dto.request.CreateUserRequest;
import com.islamshariful.authservice.dto.request.UpdateUserRolesRequest;
import com.islamshariful.authservice.dto.response.PageResponse;
import com.islamshariful.authservice.dto.response.UserResponse;
import com.islamshariful.authservice.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identity administration within the caller's tenant.
 *
 * <p>{@code @PreAuthorize} is declared per method rather than relying solely on the URL rules in
 * {@code SecurityConfig}. The two are complementary: path matching is easy to defeat by adding a route,
 * while a method-level annotation travels with the method. Neither is what confines these operations to one
 * tenant — that is the persistence layer, and it would hold even if this annotation were deleted.
 */
@RestController
@RequestMapping(path = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('TENANT_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User administration", description = "Tenant-scoped identity management (TENANT_ADMIN only)")
// Declared on the class because it is true of every operation here and of nothing else in the service:
// these are the only endpoints behind a role check, so they are the only ones that can refuse an
// authenticated caller.
@ApiResponse(responseCode = "403", description = "Authenticated, but not a TENANT_ADMIN")
public class UserAdminController {

    private final UserAccountService userAccountService;

    public UserAdminController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping
    @Operation(
            summary = "Create a user in the caller's tenant",
            description = "The tenant comes from the caller's token; there is no way to name a different one.")
    @ApiResponse(
            responseCode = "201",
            description = "User created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered in this tenant", content = @Content)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userAccountService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List users in the caller's tenant")
    public PageResponse<UserResponse> list(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return userAccountService.list(pageable);
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "Fetch one user",
            description = "An id belonging to another tenant returns 404, not 403 -- from this tenant's "
                    + "perspective the row genuinely does not exist, and 403 would confirm that it does.")
    @ApiResponse(
            responseCode = "200",
            description = "The user",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such user in this tenant", content = @Content)
    public UserResponse get(@PathVariable UUID userId) {
        return userAccountService.get(userId);
    }

    @PutMapping("/{userId}/roles")
    @Operation(
            summary = "Replace a user's roles",
            description = "PUT, so the body is the complete desired set. Refuses to remove the tenant's last administrator.")
    @ApiResponse(
            responseCode = "200",
            description = "The user, with its new roles",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "409", description = "Would leave the tenant with no administrator", content = @Content)
    public UserResponse replaceRoles(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRolesRequest request) {
        return userAccountService.replaceRoles(userId, request.roles());
    }
}
