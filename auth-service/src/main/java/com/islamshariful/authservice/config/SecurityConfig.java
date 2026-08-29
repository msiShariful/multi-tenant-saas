package com.islamshariful.authservice.config;

import com.islamshariful.authservice.security.TenantContextFilter;
import com.islamshariful.authservice.security.TenantJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/**
 * The service's security posture.
 *
 * <p>auth-service is unusual in being both the issuer of tokens and a resource server that consumes them:
 * {@code /me}, logout and user administration are protected by the same tokens the login endpoint mints.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Paths that must work before a caller has a token. Everything not listed requires one. */
    private static final String[] PUBLIC_GET_PATHS = {
        "/.well-known/jwks.json",
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TenantJwtAuthenticationConverter jwtAuthenticationConverter,
            TenantContextFilter tenantContextFilter,
            AuthenticationEntryPoint problemDetailAuthenticationEntryPoint,
            AccessDeniedHandler problemDetailAccessDeniedHandler)
            throws Exception {

        http
                // CSRF defends session cookies, which are what a browser attaches automatically. This API
                // carries no cookies and no session, so there is nothing for a forged request to ride on --
                // and a token in an Authorization header is never sent cross-site by the browser.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                        .permitAll()
                        // Sign-up bootstraps a tenant that has no members yet, so nobody could be authorised.
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                        .permitAll()
                        // Both exchange a credential for tokens; requiring a token would be circular.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/token/refresh")
                        .permitAll()
                        // Default deny. A new controller is protected the moment it is written, rather than
                        // whenever somebody remembers to add a rule for it.
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemDetailAuthenticationEntryPoint)
                        .accessDeniedHandler(problemDetailAccessDeniedHandler))
                // Also set outside oauth2ResourceServer: the former covers bearer-token failures, this covers
                // everything else (no credentials at all, method-security denials).
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailAuthenticationEntryPoint)
                        .accessDeniedHandler(problemDetailAccessDeniedHandler))
                .headers(headers -> headers
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentTypeOptions(Customizer.withDefaults())
                        // HSTS is set at the edge, where TLS actually terminates; emitting it here would be
                        // cargo cult on a plaintext internal listener.
                        .httpStrictTransportSecurity(hsts -> hsts.disable()))
                // Runs once the bearer token has been validated, so the tenant it publishes is one this
                // service signed rather than one the caller asserted.
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Delegating encoder: hashes are stored with their algorithm id ({@code {bcrypt}$2a$10$...}).
     *
     * <p>That prefix is what makes an algorithm migration possible without a flag day — point the default at
     * Argon2 later and existing bcrypt hashes keep verifying, each one upgraded on the owner's next
     * successful login. A bare {@code BCryptPasswordEncoder} locks the choice in for the life of the data.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
