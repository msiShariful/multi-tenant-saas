package com.islamshariful.userservice.config;

import com.islamshariful.userservice.security.TenantContextFilter;
import com.islamshariful.userservice.security.TenantJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * user-service is a resource server and nothing else — it verifies the tokens auth-service issues and
 * has no login of its own.
 *
 * <p>Without this class Boot's default resource-server chain requires a token for <em>every</em> path,
 * which locks out the two kinds of caller that cannot present one: the container's own healthcheck, and
 * a browser loading the API docs. A permanently unhealthy container is the more expensive of the two —
 * an orchestrator restarts it forever and anything with {@code depends_on: service_healthy} never starts.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Callers that cannot hold a token: the container healthcheck and the docs page. */
    private static final String[] PUBLIC_GET_PATHS = {
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
            TenantContextFilter tenantContextFilter)
            throws Exception {
        http
                // No session and no cookie, so there is nothing for a forged cross-site request to ride
                // on; a bearer token is never attached by the browser automatically.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                        .permitAll()
                        // Default deny: a new controller is protected the moment it is written, rather
                        // than whenever someone remembers to add a rule for it.
                        .anyRequest()
                        .authenticated())
                // Signature, issuer and audience are all checked; see the resourceserver block in
                // application.yaml. This service holds no private key and cannot mint a token.
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .headers(headers -> headers
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentTypeOptions(Customizer.withDefaults())
                        // HSTS belongs at the edge where TLS terminates, not on a plaintext internal listener.
                        .httpStrictTransportSecurity(hsts -> hsts.disable()))
                // Runs once the bearer token is validated, so the tenant it publishes is one this service
                // verified rather than one the caller asserted.
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
