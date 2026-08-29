package com.islamshariful.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI userServiceOpenApi(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return new OpenAPI()
                .info(new Info()
                        .title("TenantBase :: user-service")
                        .version("v1")
                        .description(
                                """
                                Tenant-scoped user profiles.

                                **This service issues no tokens.** It verifies the ones auth-service signs,
                                using the public key from its JWKS endpoint, so get a token there first:

                                1. `POST /api/v1/auth/login` on auth-service (default `http://localhost:8081`).
                                2. Paste the `accessToken` into **Authorize** above — the token only, no
                                   `Bearer ` prefix.

                                **Profiles are provisioned just in time.** `GET /api/v1/profiles/me` always
                                succeeds for a valid token: if no row exists yet, one is created from the
                                token's claims. Users are created in auth-service, which has no access to
                                this service's database.

                                **Tenant isolation** — every query is restricted to the token's `tenant_id`
                                by the persistence layer rather than by hand-written predicates. A user id
                                from another tenant reads as 404, never 403.

                                **Errors** are RFC 9457 `application/problem+json` with a stable `code`
                                member; branch on `code`, not on `detail`.""")
                        .contact(new Contact().name("Shariful Islam").url("https://github.com/msiShariful"))
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                """
                                                Issued by auth-service, not by this service.

                                                1. `POST /api/v1/auth/login` on auth-service.
                                                2. Copy `accessToken` from the response and paste it below.

                                                Paste the token **only** — Swagger adds the `Bearer ` prefix.
                                                Expected issuer: %s
                                                """
                                                        .formatted(issuer))));
    }
}
