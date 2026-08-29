package com.islamshariful.authservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The tests that justify the architecture. Everything else in the service is ordinary CRUD; these are the
 * ones that would matter if they regressed.
 */
@DisplayName("Tenant isolation")
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final String SHARED_EMAIL = "admin@shared.example";

    @Test
    @DisplayName("the same email can hold an account in two different tenants")
    void emailIsUniquePerTenantNotGlobally() throws Exception {
        registerTenant("alpha", SHARED_EMAIL);

        // Would fail against a global unique index on email -- the usual single-tenant default, and a
        // design mistake that is very expensive to undo once real accounts exist.
        registerTenant("beta", SHARED_EMAIL);

        Tokens alpha = login("alpha", SHARED_EMAIL);
        Tokens beta = login("beta", SHARED_EMAIL);

        // Same credentials, different identities: the tenant slug is part of the credential set.
        String alphaUserId = currentUserId(alpha);
        String betaUserId = currentUserId(beta);
        org.assertj.core.api.Assertions.assertThat(alphaUserId).isNotEqualTo(betaUserId);
    }

    @Test
    @DisplayName("a listing returns only the caller's own tenant")
    void listingIsScopedToTheCallersTenant() throws Exception {
        registerTenant("alpha", SHARED_EMAIL);
        registerTenant("beta", SHARED_EMAIL);
        Tokens alpha = login("alpha", SHARED_EMAIL);

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, alpha.bearer()))
                .andExpect(status().isOk())
                // Two administrators exist in the database; this tenant may see exactly one of them.
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("reading another tenant's user by id is a 404, not a 403")
    void crossTenantReadIsNotFound() throws Exception {
        registerTenant("alpha", SHARED_EMAIL);
        AbstractIntegrationTest.Tenant beta = registerTenant("beta", SHARED_EMAIL);
        Tokens alpha = login("alpha", SHARED_EMAIL);

        // The id is real and the caller is a TENANT_ADMIN -- the only thing stopping this is that
        // Hibernate added `AND tenant_id = ?` to the lookup, so from alpha's view the row does not exist.
        // 404 rather than 403 matters: 403 would confirm the id belongs to somebody.
        mockMvc.perform(get("/api/v1/users/" + beta.administratorId())
                        .header(HttpHeaders.AUTHORIZATION, alpha.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("writing to another tenant's user is a 404 as well")
    void crossTenantWriteIsNotFound() throws Exception {
        registerTenant("alpha", SHARED_EMAIL);
        AbstractIntegrationTest.Tenant beta = registerTenant("beta", SHARED_EMAIL);
        Tokens alpha = login("alpha", SHARED_EMAIL);

        mockMvc.perform(put("/api/v1/users/" + beta.administratorId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, alpha.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["TENANT_USER"]}"""))
                .andExpect(status().isNotFound());

        // And beta's administrator is untouched.
        Tokens beta_ = login("beta", SHARED_EMAIL);
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, beta_.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMIN"));
    }

    @Test
    @DisplayName("a token issued for one tenant cannot act on another")
    void tokensCarryTheirTenant() throws Exception {
        AbstractIntegrationTest.Tenant alpha = registerTenant("alpha", SHARED_EMAIL);
        registerTenant("beta", SHARED_EMAIL);
        Tokens beta = login("beta", SHARED_EMAIL);

        // beta's administrator presenting a perfectly valid token cannot reach alpha's rows, because the
        // scope comes from the signed tenant_id claim rather than from anything in the request.
        mockMvc.perform(get("/api/v1/users/" + alpha.administratorId())
                        .header(HttpHeaders.AUTHORIZATION, beta.bearer()))
                .andExpect(status().isNotFound());
    }
}
