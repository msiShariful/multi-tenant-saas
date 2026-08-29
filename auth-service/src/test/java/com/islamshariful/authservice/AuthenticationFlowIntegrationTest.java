package com.islamshariful.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Authentication lifecycle")
class AuthenticationFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "ada@acme.example";

    @Test
    @DisplayName("sign-up creates the tenant and its administrator and never echoes the password")
    void signUpProvisionsTenantAndAdministrator() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantName": "Acme Corporation",
                                  "tenantSlug": "acme",
                                  "adminEmail": "ADA@ACME.EXAMPLE",
                                  "adminPassword": "%s",
                                  "firstName": "Ada",
                                  "lastName": "Lovelace"
                                }""".formatted(DEFAULT_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenant.slug").value("acme"))
                .andExpect(jsonPath("$.tenant.status").value("ACTIVE"))
                // Normalised on the way in, so a later login with different casing still matches.
                .andExpect(jsonPath("$.administrator.email").value("ada@acme.example"))
                .andExpect(jsonPath("$.administrator.roles[0]").value("TENANT_ADMIN"))
                // The @TenantId column made it onto the response, which means Hibernate populated it.
                .andExpect(jsonPath("$.administrator.tenantId").isNotEmpty())
                // Nothing from the entity that is not on the DTO.
                .andExpect(jsonPath("$.administrator.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.administrator.failedLoginAttempts").doesNotExist());
    }

    @Test
    @DisplayName("a duplicate tenant slug is a 409 with a machine-readable code")
    void duplicateSlugIsRejected() throws Exception {
        registerTenant("acme", EMAIL);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantName": "Impostor",
                                  "tenantSlug": "acme",
                                  "adminEmail": "someone@else.example",
                                  "adminPassword": "%s"
                                }""".formatted(DEFAULT_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SLUG_TAKEN"));
    }

    @Test
    @DisplayName("invalid input comes back as RFC 9457 problem+json with field errors")
    void validationFailuresAreStructured() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantName": "Acme",
                                  "tenantSlug": "Not A Slug",
                                  "adminEmail": "not-an-email",
                                  "adminPassword": "short"
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    @DisplayName("unknown tenant, unknown user and wrong password are indistinguishable")
    void failedLoginsDoNotLeakWhichPartWasWrong() throws Exception {
        registerTenant("acme", EMAIL);

        String unknownTenant = failedLoginBody("nosuchtenant", EMAIL, DEFAULT_PASSWORD);
        String unknownUser = failedLoginBody("acme", "nobody@acme.example", DEFAULT_PASSWORD);
        String wrongPassword = failedLoginBody("acme", EMAIL, "definitely not the password");

        assertThat(unknownTenant).isEqualTo(unknownUser).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("an unauthenticated call gets problem+json, not an empty 401")
    void missingTokenProducesAProblemDocument() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("a garbage bearer token is rejected as 401, not 500")
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the account locks after the configured number of failures and stays locked for a correct password")
    void repeatedFailuresLockTheAccount() throws Exception {
        registerTenant("acme", EMAIL);

        // application-test.yaml sets max-failed-attempts to 3.
        failedLoginBody("acme", EMAIL, "wrong-1");
        failedLoginBody("acme", EMAIL, "wrong-2");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantSlug": "acme", "email": "%s", "password": "wrong-3"}""".formatted(EMAIL)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());

        // The counter survived the rejected requests, which it only does because attemptLogin returns the
        // failure instead of throwing inside the transaction that recorded it.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantSlug": "acme", "email": "%s", "password": "%s"}"""
                                .formatted(EMAIL, DEFAULT_PASSWORD)))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("a successful login resets the failure counter")
    void successfulLoginClearsFailures() throws Exception {
        registerTenant("acme", EMAIL);

        failedLoginBody("acme", EMAIL, "wrong-1");
        failedLoginBody("acme", EMAIL, "wrong-2");
        login("acme", EMAIL);

        // Back to a clean slate: two more failures must not be enough to trip the threshold.
        failedLoginBody("acme", EMAIL, "wrong-3");
        failedLoginBody("acme", EMAIL, "wrong-4");
        login("acme", EMAIL);
    }

    @Test
    @DisplayName("changing a password revokes every refresh token the user holds")
    void passwordChangeRevokesSessions() throws Exception {
        registerTenant("acme", EMAIL);
        Tokens session = login("acme", EMAIL);

        mockMvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "a brand new passphrase"}"""
                                .formatted(DEFAULT_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(session.refreshToken())))
                .andExpect(status().isUnauthorized());

        login("acme", EMAIL, "a brand new passphrase");
    }

    @Test
    @DisplayName("the JWKS endpoint publishes the public key and nothing else")
    void jwksExposesOnlyPublicMaterial() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").value("test-key"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                // 'd' is the private exponent. Its presence here would be the whole game.
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    /** Performs a login expected to fail and returns the response body, minus the timestamp that varies. */
    private String failedLoginBody(String slug, String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantSlug": "%s", "email": "%s", "password": "%s"}"""
                                .formatted(slug, email, password)))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "%s|%s|%s"
                .formatted(
                        JsonPath.read(body, "$.code").toString(),
                        JsonPath.read(body, "$.detail").toString(),
                        JsonPath.read(body, "$.status").toString());
    }
}
