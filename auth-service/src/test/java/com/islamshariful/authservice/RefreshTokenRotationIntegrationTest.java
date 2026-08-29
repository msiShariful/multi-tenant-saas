package com.islamshariful.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Refresh token rotation and reuse detection")
class RefreshTokenRotationIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "ada@acme.example";

    @Test
    @DisplayName("refreshing returns a new pair and consumes the old refresh token")
    void refreshRotatesTheToken() throws Exception {
        registerTenant("acme", EMAIL);
        Tokens first = login("acme", EMAIL);

        Tokens second = refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        // Single use: the token just spent is dead even though it has not expired.
        mockMvc.perform(refreshRequest(first.refreshToken())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("replaying a consumed token revokes the whole rotation family")
    void reuseRevokesTheFamily() throws Exception {
        registerTenant("acme", EMAIL);
        Tokens first = login("acme", EMAIL);
        Tokens second = refresh(first.refreshToken());

        // Someone -- the legitimate client or a thief holding a copy -- replays the spent token. There is no
        // way to tell which, so the safe assumption is that the chain leaked.
        mockMvc.perform(refreshRequest(first.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // The consequence: the currently valid token dies too. Whoever holds it must log in again, which is
        // the point -- an attacker who stole the chain no longer has a way back in.
        mockMvc.perform(refreshRequest(second.refreshToken())).andExpect(status().isUnauthorized());

        login("acme", EMAIL);
    }

    @Test
    @DisplayName("an unknown refresh token is rejected without a stack trace")
    void unknownTokenIsRejected() throws Exception {
        registerTenant("acme", EMAIL);

        mockMvc.perform(refreshRequest("clearly-not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("logging out kills the session it was given")
    void logoutRevokesTheSession() throws Exception {
        registerTenant("acme", EMAIL);
        Tokens session = login("acme", EMAIL);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(session.refreshToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(refreshRequest(session.refreshToken())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logging out with an unknown token still reports success")
    void logoutDoesNotProbeTokenValidity() throws Exception {
        registerTenant("acme", EMAIL);
        Tokens session = login("acme", EMAIL);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "some-token-that-does-not-exist"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("one device logging out leaves the other device signed in")
    void familiesAreIndependentPerLogin() throws Exception {
        registerTenant("acme", EMAIL);
        Tokens laptop = login("acme", EMAIL);
        Tokens phone = login("acme", EMAIL);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, laptop.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(laptop.refreshToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(refreshRequest(laptop.refreshToken())).andExpect(status().isUnauthorized());
        // A family per login is what makes this possible: revocation is per device, not per account.
        refresh(phone.refreshToken());
    }

    private Tokens refresh(String refreshToken) throws Exception {
        String json = mockMvc.perform(refreshRequest(refreshToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Tokens(JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.refreshToken"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refreshRequest(String token) {
        return post("/api/v1/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}""".formatted(token));
    }
}
