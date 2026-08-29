package com.islamshariful.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS at the edge, so there is one policy rather than one per service.
 *
 * <p>Configured as a bean rather than through properties because Spring Cloud Gateway's servlet
 * variant publishes no CORS configuration keys — this is plain Spring MVC, and the natural-looking
 * {@code spring.web.cors.*} does not exist. Binding to a property that is not there fails silently:
 * the application starts, and the preflight is simply never answered.
 *
 * <p>Ordered ahead of everything so a preflight is answered before routing gets a chance to 404 an
 * {@code OPTIONS} to a path no route matches.
 *
 * <p>Note that the Next.js frontend does not need any of this — as a BFF it calls from the server,
 * where CORS does not apply. This is for browser clients that address the gateway directly.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        // Exact origins, never addAllowedOriginPattern("*"): with credentials enabled the browser
        // rejects a wildcard anyway, and allowing one would be handing out authenticated sessions.
        properties.allowedOrigins().forEach(configuration::addAllowedOrigin);
        properties.allowedMethods().forEach(configuration::addAllowedMethod);
        properties.allowedHeaders().forEach(configuration::addAllowedHeader);
        configuration.setAllowCredentials(properties.allowCredentials());
        configuration.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            CorsFilter corsFilter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(corsFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
