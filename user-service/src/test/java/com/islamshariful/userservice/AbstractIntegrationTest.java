package com.islamshariful.userservice;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for tests that exercise the service end to end against a real PostgreSQL.
 *
 * <p>A real database is not optional: the behaviour most worth testing — the tenant predicate Hibernate
 * appends to every statement — lives in generated SQL, and an in-memory database with a hand-written
 * schema would be testing a different system.
 *
 * <p>No {@code @Transactional}. A rollback-per-test binds a persistence context to the thread before the
 * request runs, which would defeat the tenant scoping the filter is supposed to establish. Tables are
 * truncated between tests instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TestTokens.TokenMinter tokens;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE user_profiles CASCADE");
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected long profileCount() {
        Long count = jdbcTemplate.queryForObject("select count(*) from user_profiles", Long.class);
        return count == null ? 0 : count;
    }

    protected long profileCountIn(UUID tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from user_profiles where tenant_id = ?", Long.class, tenantId);
        return count == null ? 0 : count;
    }
}
