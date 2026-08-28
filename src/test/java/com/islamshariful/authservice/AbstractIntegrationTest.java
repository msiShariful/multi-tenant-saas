package com.islamshariful.authservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for tests that exercise the service end to end against a real PostgreSQL.
 *
 * <p>A real database is not optional here. The behaviour most worth testing — that Hibernate appends the
 * tenant discriminator to every statement — lives in generated SQL, and an in-memory H2 with a hand-written
 * schema would test a different system. The same container also proves the Flyway migrations apply and that
 * {@code ddl-auto: validate} agrees with them.
 *
 * <p>One container is shared by every test class: it is started once in a static initialiser and reused,
 * because Spring's context cache keeps the same application context alive across classes anyway.
 *
 * <p>Note the absence of {@code @Transactional}. A rollback-per-test would bind a persistence context to the
 * thread before the request ran, which {@code TenantScope} correctly refuses to work under. Tables are
 * truncated between tests instead — closer to what actually happens in production, and it keeps the guard honest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String DEFAULT_PASSWORD = "correct horse battery staple";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        // roles is left alone: it is reference data owned by the V2 migration, not test fixture.
        jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens, user_roles, users, tenants CASCADE");
    }

    // ------------------------------------------------------------- fixtures

    /** Provisions a tenant with an administrator and returns the created ids. */
    protected Tenant registerTenant(String slug, String adminEmail) throws Exception {
        String body =
                """
                {
                  "tenantName": "%s Ltd",
                  "tenantSlug": "%s",
                  "adminEmail": "%s",
                  "adminPassword": "%s",
                  "firstName": "Ada",
                  "lastName": "Lovelace"
                }"""
                        .formatted(slug, slug, adminEmail, DEFAULT_PASSWORD);

        String json = mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return new Tenant(
                JsonPath.read(json, "$.tenant.id"),
                slug,
                adminEmail,
                JsonPath.read(json, "$.administrator.id"));
    }

    protected Tokens login(String tenantSlug, String email) throws Exception {
        return login(tenantSlug, email, DEFAULT_PASSWORD);
    }

    protected Tokens login(String tenantSlug, String email, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantSlug": "%s", "email": "%s", "password": "%s"}"""
                                .formatted(tenantSlug, email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Tokens(JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.refreshToken"));
    }

    protected String currentUserId(Tokens tokens) throws Exception {
        String json = mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, tokens.bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    protected record Tenant(String id, String slug, String adminEmail, String administratorId) {}

    protected record Tokens(String accessToken, String refreshToken) {

        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
