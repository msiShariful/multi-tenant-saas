package com.islamshariful.userservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Makes security failures produce the same problem+json body as everything else.
 *
 * <p>Both exceptions are thrown inside the servlet filter chain, upstream of {@code DispatcherServlet}, so
 * {@code @RestControllerAdvice} never sees them. Left alone, Spring Security writes an empty 401/403 and
 * the API has two error formats — one for business errors and one for auth.
 *
 * <p>Rather than duplicating the JSON, both handlers hand the exception to the MVC
 * {@link HandlerExceptionResolver}, which dispatches it to {@code GlobalExceptionHandler}.
 */
@Configuration(proxyBeanMethods = false)
public class ProblemDetailAuthenticationHandlers {

    private final HandlerExceptionResolver resolver;

    public ProblemDetailAuthenticationHandlers(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Bean
    public AuthenticationEntryPoint problemDetailAuthenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) ->
                resolver.resolveException(request, response, null, exception);
    }

    @Bean
    public AccessDeniedHandler problemDetailAccessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) ->
                resolver.resolveException(request, response, null, exception);
    }
}
