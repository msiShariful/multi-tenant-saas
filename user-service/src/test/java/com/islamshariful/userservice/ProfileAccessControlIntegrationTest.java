package com.islamshariful.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Access control")
class ProfileAccessControlIntegrationTest extends AbstractIntegrationTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private String memberToken() {
        return tokens.mint(memberId, tenantId, "acme", "member@acme.example", "TENANT_USER");
    }

    private String adminToken() {
        return tokens.mint(adminId, tenantId, "acme", "admin@acme.example", "TENANT_ADMIN");
    }

    @Test
    @DisplayName("no token is 401 in problem+json, not an empty body")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("a token this service did not sign is rejected")
    void foreignSignatureIsRejected() throws Exception {
        // Structurally a JWT, but signed by nobody we trust.
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token missing the tenant claim is 401, not 500")
    void tokenWithoutTenantClaimIsRejected() throws Exception {
        // The signature verifies; the claim contract does not hold. That is malformed, not merely
        // unauthorised, and must not escape as a 500 that leaks internals.
        String token = tokens.mintWithoutTenant(memberId, "member@acme.example");
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an ordinary member cannot delete a profile")
    void membersCannotDelete() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(memberToken())));

        mockMvc.perform(delete("/api/v1/profiles/" + memberId).header(HttpHeaders.AUTHORIZATION, bearer(memberToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(profileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an administrator can, and only profile data goes")
    void administratorsCanDelete() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(memberToken())));
        assertThat(profileCount()).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/profiles/" + memberId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());
        assertThat(profileCount()).isZero();

        // The account still exists in auth-service, so the member simply gets a fresh profile.
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(memberToken())))
                .andExpect(status().isOk());
        assertThat(profileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a member can still read the directory and their own profile")
    void membersRetainOrdinaryAccess() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(memberToken())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/profiles").header(HttpHeaders.AUTHORIZATION, bearer(memberToken())))
                .andExpect(status().isOk());
    }
}
