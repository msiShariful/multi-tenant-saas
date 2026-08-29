package com.islamshariful.authservice.web;

import com.islamshariful.authservice.dto.request.TenantRegistrationRequest;
import com.islamshariful.authservice.dto.response.TenantRegistrationResponse;
import com.islamshariful.authservice.service.TenantRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tenants", description = "Tenant provisioning")
public class TenantController {

    private final TenantRegistrationService tenantRegistrationService;

    public TenantController(TenantRegistrationService tenantRegistrationService) {
        this.tenantRegistrationService = tenantRegistrationService;
    }

    @PostMapping
    @Operation(
            summary = "Register a tenant and its first administrator",
            description = """
                    The only unauthenticated write in the service, and the bootstrap for everything else:
                    tenants have no members until this runs, so there is nobody who could be authorised to
                    call it. In a commercial deployment it would sit behind billing or an invite code.

                    Both records are created in one transaction -- a tenant with no administrator would be
                    unreachable forever.

                    Returns the created resources rather than tokens; the client then logs in through
                    /api/v1/auth/login exactly as it will on every later visit.""")
    @ApiResponse(responseCode = "201", description = "Tenant provisioned")
    @ApiResponse(responseCode = "409", description = "Tenant slug already taken", content = @io.swagger.v3.oas.annotations.media.Content)
    public ResponseEntity<TenantRegistrationResponse> register(@Valid @RequestBody TenantRegistrationRequest request) {
        TenantRegistrationResponse response = tenantRegistrationService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + response.tenant().id())).body(response);
    }
}
