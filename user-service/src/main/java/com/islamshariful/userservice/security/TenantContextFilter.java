package com.islamshariful.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propagates the authenticated caller's tenant into {@link TenantContext} for the duration of the request.
 *
 * <p>Registered after the bearer-token filter, so the tenant it publishes came from a signature this
 * service verified against auth-service's public key — not from a header a caller could set.
 *
 * <p><b>Why user-service needs no equivalent of auth-service's {@code TenantScope}.</b> Hibernate fixes a
 * session's tenant when the session opens, so the tenant must be established first. In auth-service that
 * is genuinely hard: login has to find a user before it knows which tenant to scope to. Here the tenant
 * arrives already validated, and this filter runs before any transaction can start — with
 * {@code open-in-view} off, nothing has opened a persistence context by the time it does. The ordering is
 * correct by construction rather than by careful arrangement.
 *
 * <p>The {@code finally} block is load-bearing: request threads are pooled, so a context left behind would
 * be inherited by whichever tenant's request lands on that thread next.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter implements Ordered {

    private static final String MDC_TENANT = "tenantId";
    private static final String MDC_USER = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean scoped = false;
        if (authentication instanceof TenantAuthentication tenantAuthentication) {
            AuthenticatedUser caller = tenantAuthentication.getPrincipal();
            TenantContext.set(caller.tenantId());
            MDC.put(MDC_TENANT, caller.tenantId().toString());
            MDC.put(MDC_USER, caller.userId().toString());
            scoped = true;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (scoped) {
                TenantContext.clear();
                MDC.remove(MDC_TENANT);
                MDC.remove(MDC_USER);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
