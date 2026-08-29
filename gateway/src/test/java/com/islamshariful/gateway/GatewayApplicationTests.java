package com.islamshariful.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.islamshariful.gateway.config.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.RouteProperties;

/**
 * Guards the two things about this service that fail <em>silently</em>.
 *
 * <p>Both route configuration and CORS configuration bind from properties, and a wrong prefix binds to
 * nothing at all: the application starts cleanly, reports healthy, and does not route or does not
 * answer a preflight. Spring Cloud Gateway 5 moved routes under {@code spring.cloud.gateway.server.webmvc},
 * and {@code spring.web.cors.*} — which looks exactly like a real property — does not exist. A
 * {@code contextLoads()} test passes happily through either mistake.
 */
@SpringBootTest
@DisplayName("Gateway configuration")
class GatewayApplicationTests {

    @Autowired
    private GatewayMvcProperties gatewayProperties;

    @Autowired
    private CorsProperties corsProperties;

    @Test
    @DisplayName("the route table actually bound from configuration")
    void routesAreBound() {
        List<RouteProperties> routes = gatewayProperties.getRoutes();
        assertThat(routes)
                .as("no routes bound — check the property prefix is spring.cloud.gateway.server.webmvc.routes")
                .isNotEmpty();
        assertThat(routes).extracting(RouteProperties::getId).contains("auth", "auth-jwks", "profiles");
    }

    @Test
    @DisplayName("identity and profile paths go to different services")
    void thePathsThatAreEasyToConfusePointAtDifferentServices() {
        // /api/v1/users is auth-service (credentials, roles); /api/v1/profiles is user-service
        // (display data). Sending either to the wrong place produces 404s that look like data loss.
        String auth = routeUri("auth");
        String profiles = routeUri("profiles");
        assertThat(auth).isNotEqualTo(profiles);
        assertThat(predicatesOf("auth")).anyMatch(p -> p.contains("/api/v1/users/**"));
        assertThat(predicatesOf("profiles")).anyMatch(p -> p.contains("/api/v1/profiles/**"));
    }

    @Test
    @DisplayName("CORS bound, and never a wildcard origin")
    void corsIsBoundAndNotAWildcard() {
        assertThat(corsProperties.allowedOrigins()).isNotEmpty();
        // A wildcard is incompatible with credentials and would hand an authenticated session to any
        // site on the internet.
        assertThat(corsProperties.allowedOrigins()).doesNotContain("*");
    }

    private String routeUri(String id) {
        return gatewayProperties.getRoutes().stream()
                .filter(route -> id.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no route with id " + id))
                .getUri()
                .toString();
    }

    private List<String> predicatesOf(String id) {
        return gatewayProperties.getRoutes().stream()
                .filter(route -> id.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no route with id " + id))
                .getPredicates()
                .stream()
                .map(Object::toString)
                .toList();
    }
}
