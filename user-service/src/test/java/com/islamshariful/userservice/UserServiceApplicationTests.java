package com.islamshariful.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@DisplayName("Application wiring")
class UserServiceApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("the context starts against a real database")
    void contextLoads() {
        assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("it is wired as a resource server, so a JwtDecoder exists")
    void isAResourceServer() {
        assertThat(jwtDecoder).isNotNull();
    }

    @Test
    @DisplayName("endpoints are closed by default")
    void endpointsRequireAToken() throws Exception {
        // Nothing is mapped yet, so this only proves the filter chain refuses before routing --
        // an unauthenticated request must not reach a 404 that confirms what does not exist.
        mockMvc.perform(get("/api/v1/profiles/me")).andExpect(status().isUnauthorized());
    }
}
