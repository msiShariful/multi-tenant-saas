package com.islamshariful.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI authServiceOpenApi(JwtProperties jwtProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title("TenantBase :: auth-service")
                        .version("v1")
                        .description(
                                """
                                Authentication, tenant provisioning and role-based access control for a
                                multi-tenant SaaS platform.

                                **Getting a token**
                                1. `POST /api/v1/tenants` to provision a tenant and its administrator.
                                2. `POST /api/v1/auth/login` with the tenant slug, email and password.
                                3. Send `Authorization: Bearer <accessToken>` on subsequent calls, and use
                                   **Authorize** above to do the same from this page.

                                **Tenant isolation** — every access token carries a `tenant_id` claim, and
                                every tenant-scoped query is restricted to it by Hibernate rather than by
                                hand-written predicates. An id from another tenant reads as 404, never 403.

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
                                                How to get one:
                                                1. POST /api/v1/tenants — provisions a tenant and its administrator (skip if you already have an account).
                                                2. POST /api/v1/auth/login — send the tenant slug, email and password.
                                                3. Copy `accessToken` from the response and paste it below.

                                                Paste the token **only** — Swagger adds the `Bearer ` prefix itself.
                                                Tokens are valid for %s and are kept across page reloads.
                                                Issuer: %s
                                                """
                                                        .formatted(
                                                                humanise(jwtProperties.accessTokenTtl()),
                                                                jwtProperties.issuer()))));
    }

    /** {@code Duration.toString()} renders ISO-8601 ("PT15M"), which is not what a reader wants in a dialog. */
    private static String humanise(java.time.Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes > 0 && duration.toSecondsPart() == 0) {
            return minutes == 1 ? "1 minute" : minutes + " minutes";
        }
        return duration.toSeconds() + " seconds";
    }
}
