package com.islamshariful.authservice.security;

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
 * <p>Registered <em>after</em> the bearer-token filter, so by the time it runs the token's signature,
 * issuer, audience and expiry have all been checked — the tenant id it copies is one this service signed.
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
