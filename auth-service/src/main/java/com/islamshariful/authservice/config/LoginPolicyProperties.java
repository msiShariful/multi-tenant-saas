package com.islamshariful.authservice.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Brute-force policy for the login endpoint.
 *
 * <p>This is per-account throttling, which stops credential stuffing against a known email. It is not a
 * substitute for per-IP rate limiting — that belongs at the gateway, where it can be applied before a
 * request costs this service a database round trip and a bcrypt verification.
 */
@Validated
@ConfigurationProperties(prefix = "tenantbase.security.login")
public record LoginPolicyProperties(@Positive int maxFailedAttempts, @NotNull Duration lockoutDuration) {}
