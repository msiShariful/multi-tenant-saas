package com.islamshariful.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * TenantBase user-service — tenant-scoped user profiles.
 *
 * <p>A pure resource server: it verifies the access tokens auth-service issues and never mints one.
 * The two services share only a user id — credentials and roles stay in auth-service, profile data
 * lives here, and neither can read the other's database.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded because there is nothing for it to do.
 * Callers arrive with a bearer token that has already been validated against auth-service's public key;
 * left in, Boot would create an in-memory user and print a generated password at every startup, which
 * is dead weight and easy to mistake for a real account.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
