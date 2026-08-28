package com.islamshariful.authservice.security;

import java.io.Serial;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authentication for a validated access token, carrying {@link AuthenticatedUser} as its principal.
 *
 * <p>The stock {@code JwtAuthenticationToken} exposes the raw {@link Jwt} as principal, which pushes claim
 * parsing into every controller. Substituting the principal here keeps that parsing in exactly one place.
 */
public class TenantAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Jwt token;
    private final AuthenticatedUser principal;

    public TenantAuthentication(Jwt token, AuthenticatedUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.token = token;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public String getCredentials() {
        return token.getTokenValue();
    }

    public Jwt getToken() {
        return token;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
