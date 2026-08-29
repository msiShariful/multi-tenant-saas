package com.islamshariful.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * The tests that justify the architecture. Everything else here is ordinary CRUD; these are the ones
 * that would matter if they regressed.
 */
@DisplayName("Tenant isolation")
class ProfileTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private final UUID alphaTenant = UUID.randomUUID();
    private final UUID betaTenant = UUID.randomUUID();
    private final UUID alphaUser = UUID.randomUUID();
    private final UUID betaUser = UUID.randomUUID();

    private String alphaToken;
    private String betaToken;

    @BeforeEach
    void provisionBothTenants() throws Exception {
        // Deliberately the same email in both tenants: auth-service makes email unique per tenant, so
        // one address can identify two different people, and nothing here may conflate them.
        alphaToken = tokens.mint(alphaUser, alphaTenant, "alpha", "same@shared.example", "TENANT_ADMIN");
        betaToken = tokens.mint(betaUser, betaTenant, "beta", "same@shared.example", "TENANT_ADMIN");
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(alphaToken)));
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(betaToken)));
    }

    @Test
    @DisplayName("the same email in two tenants yields two distinct profiles")
    void oneEmailTwoTenants() {
        assertThat(profileCount()).isEqualTo(2);
        assertThat(profileCountIn(alphaTenant)).isEqualTo(1);
        assertThat(profileCountIn(betaTenant)).isEqualTo(1);
    }

    @Test
    @DisplayName("the directory shows only the caller's own tenant")
    void directoryIsScopedToTheCallersTenant() throws Exception {
        // Two profiles exist in the table; this tenant may see exactly one of them.
        mockMvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(alphaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tenantId").value(alphaTenant.toString()));
    }

    @Test
    @DisplayName("reading another tenant's profile is 404, not 403")
    void crossTenantReadIsNotFound() throws Exception {
        // A real id, and the caller is a TENANT_ADMIN. The only thing stopping this is the tenant
        // predicate Hibernate added, so from alpha's point of view the row does not exist. 403 would
        // confirm that it does.
        mockMvc.perform(get("/api/v1/profiles/" + betaUser).header(HttpHeaders.AUTHORIZATION, bearer(alphaToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("deleting another tenant's profile is 404 and changes nothing")
    void crossTenantDeleteIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/profiles/" + betaUser).header(HttpHeaders.AUTHORIZATION, bearer(alphaToken)))
                .andExpect(status().isNotFound());

        assertThat(profileCountIn(betaTenant)).as("beta's profile survives alpha's delete").isEqualTo(1);
    }

    @Test
    @DisplayName("the directory search cannot reach across tenants either")
    void searchIsScopedToo() throws Exception {
        // Both profiles share this email, so an unscoped search would return two.
        mockMvc.perform(get("/api/v1/profiles?search=same@shared.example")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alphaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(alphaUser.toString()));
    }
}
