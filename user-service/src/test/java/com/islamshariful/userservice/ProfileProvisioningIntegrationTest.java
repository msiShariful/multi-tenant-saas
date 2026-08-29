package com.islamshariful.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.islamshariful.userservice.security.AuthenticatedUser;
import com.islamshariful.userservice.security.TenantContext;
import com.islamshariful.userservice.service.UserProfileService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Just-in-time provisioning")
class ProfileProvisioningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserProfileService userProfileService;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private String token(String email) {
        return tokens.mint(userId, tenantId, "acme", email, "TENANT_USER");
    }

    @Test
    @DisplayName("the first request creates the profile from the token's claims")
    void firstRequestProvisions() throws Exception {
        assertThat(profileCount()).isZero();

        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token("ada@acme.example"))))
                .andExpect(status().isOk())
                // The primary key IS the token's subject -- no surrogate, no lookup table.
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                // tenant_id was filled in by Hibernate from the session's tenant, not passed by the caller.
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.email").value("ada@acme.example"))
                // Something recognisable in the directory before the user sets anything.
                .andExpect(jsonPath("$.displayName").value("ada"));

        assertThat(profileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a second request reuses the row rather than creating another")
    void secondRequestIsIdempotent() throws Exception {
        String token = token("ada@acme.example");
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));

        assertThat(profileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the email projection follows the token when it changes")
    void emailProjectionIsRefreshedFromTheToken() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token("old@acme.example"))))
                .andExpect(jsonPath("$.email").value("old@acme.example"));

        // auth-service is the source of truth; a changed address arrives only in the next token.
        mockMvc.perform(get("/api/v1/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token("new@acme.example"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@acme.example"));

        assertThat(profileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("updating provisions first, so PUT works before any GET")
    void updateProvisionsOnTheWayThrough() throws Exception {
        assertThat(profileCount()).isZero();

        mockMvc.perform(put("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("ada@acme.example")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ada Lovelace","bio":"Analytical engines.","timeZone":"Asia/Dhaka"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Dhaka"));

        assertThat(profileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT is a replace: an omitted field is cleared, not left alone")
    void putReplacesRatherThanMerges() throws Exception {
        String token = token("ada@acme.example");
        mockMvc.perform(put("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ada","bio":"First draft."}"""))
                .andExpect(jsonPath("$.bio").value("First draft."));

        mockMvc.perform(put("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ada"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist());
    }

    @Test
    @DisplayName("concurrent first requests produce one row, not a duplicate-key error")
    void concurrentFirstRequestsRaceSafely() throws Exception {
        // A single-page app firing two requests on load is enough to hit this. Both see no row, both
        // insert, and the primary key rejects the loser -- which the service must recover from rather
        // than surface as a 500 on somebody's first ever login.
        int attempts = 8;
        AuthenticatedUser caller = new AuthenticatedUser(
                userId, tenantId, "acme", "ada@acme.example", Set.of("TENANT_USER"));

        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            List<Callable<Object>> tasks = java.util.Collections.nCopies(
                    attempts,
                    () -> TenantContext.callWith(tenantId, () -> userProfileService.getOrCreateOwnProfile(caller)));
            List<Future<Object>> results = pool.invokeAll(tasks);
            for (Future<Object> result : results) {
                assertThat(result.get()).isNotNull();
            }
        }

        assertThat(profileCount()).as("exactly one profile despite %d concurrent first requests", attempts)
                .isEqualTo(1);
    }
}
