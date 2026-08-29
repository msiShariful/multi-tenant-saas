package com.islamshariful.userservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for tests that exercise the service end to end against a real PostgreSQL.
 *
 * <p>Mirrors auth-service's harness deliberately — one pattern across the platform. The image tag is
 * pinned rather than {@code latest}, so a new upstream publish cannot change what the suite runs
 * against, and it matches the version compose runs.
 *
 * <p>One container is shared by every test class: started once in a static initialiser and reused,
 * because Spring's context cache keeps the same application context alive across classes anyway.
 *
 * <p>Note the absence of {@code @Transactional}. A rollback-per-test binds a persistence context to
 * the thread before the request runs, which is exactly what tenant scoping must not inherit. Tables
 * are truncated between tests instead.
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

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;
}
