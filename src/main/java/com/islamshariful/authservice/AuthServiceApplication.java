package com.islamshariful.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * TenantBase auth-service.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded on purpose. Left in, Boot would create an
 * in-memory user with a password printed to the log at every start — dead weight in a service that
 * authenticates against a tenant-scoped table, and an easy thing for a reader to mistake for a real account.
 * Nothing here uses {@code UserDetailsService}: identities are keyed by {@code (tenant, email)}, which its
 * single-argument contract cannot express.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
