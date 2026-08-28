package com.islamshariful.authservice.web;

import com.islamshariful.authservice.dto.request.ChangePasswordRequest;
import com.islamshariful.authservice.dto.request.LoginRequest;
import com.islamshariful.authservice.dto.request.RefreshTokenRequest;
import com.islamshariful.authservice.dto.response.TokenResponse;
import com.islamshariful.authservice.dto.response.UserResponse;
import com.islamshariful.authservice.security.AuthenticatedUser;
import com.islamshariful.authservice.service.AuthenticationService;
import com.islamshariful.authservice.service.ClientMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Login, token lifecycle and self-service credential management")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Exchange tenant-scoped credentials for a token pair",
            description = """
                    Credentials are (tenant slug, email, password): email is unique per tenant, not globally.

                    Unknown tenant, unknown email and wrong password all return the same 401 with the same
                    body, so the endpoint cannot be used to discover which tenants or accounts exist.""")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @io.swagger.v3.oas.annotations.media.Content)
    @ApiResponse(responseCode = "403", description = "Account disabled or tenant suspended", content = @io.swagger.v3.oas.annotations.media.Content)
    @ApiResponse(responseCode = "423", description = "Temporarily locked after repeated failures", content = @io.swagger.v3.oas.annotations.media.Content)
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        TokenResponse tokens = authenticationService.login(request, clientMetadata(servletRequest));
        // Tokens are credentials: keep them out of every cache between here and the client.
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(tokens);
    }

    @PostMapping("/token/refresh")
    @Operation(
            summary = "Rotate a refresh token for a new pair",
            description = """
                    Refresh tokens are single-use. Each call returns a new refresh token and invalidates the
                    one presented; replaying a consumed token is treated as a compromise and revokes every
                    token in that rotation chain.""")
    @ApiResponse(responseCode = "200", description = "Rotated")
    @ApiResponse(responseCode = "401", description = "Unknown, expired, revoked or replayed token", content = @io.swagger.v3.oas.annotations.media.Content)
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        TokenResponse tokens = authenticationService.refresh(request.refreshToken(), clientMetadata(servletRequest));
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(tokens);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Revoke the presented refresh token's session",
            description = """
                    Returns 204 whether or not the token existed, so it cannot be used to probe token validity.
                    The access token remains valid until it expires -- that is inherent to stateless tokens and
                    is why their lifetime is short.""")
    @ApiResponse(responseCode = "204", description = "Session revoked, or nothing to revoke")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(caller, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The authenticated caller's own account")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return authenticationService.currentUser(caller);
    }

    @PostMapping("/password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Change the caller's own password",
            description = "Revokes every refresh token the user holds, forcing all their devices to re-authenticate.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "401", description = "Current password is wrong", content = @io.swagger.v3.oas.annotations.media.Content)
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(caller, request);
        return ResponseEntity.noContent().build();
    }

    private static ClientMetadata clientMetadata(HttpServletRequest request) {
        // getRemoteAddr() is the real client only because server.forward-headers-strategy=framework makes
        // Spring apply X-Forwarded-For; reading that header directly would trust anything a caller sends.
        return ClientMetadata.of(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
    }
}
