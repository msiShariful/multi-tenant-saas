package com.islamshariful.userservice.security;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a validated {@link Jwt} into a {@link TenantAuthentication}.
 *
 * <p>This replaces the declarative {@code authorities-claim-name} / {@code authority-prefix} properties,
 * which map roles but leave the raw {@code Jwt} as the principal — pushing claim parsing into every
 * controller. Roles travel in the token without the {@code ROLE_} prefix, because that prefix is a Spring
 * Security implementation detail and the token is consumed by other services too.
 */
@Component
public class TenantJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedUser principal = AuthenticatedUser.from(jwt);
        List<GrantedAuthority> authorities = principal.roles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new TenantAuthentication(jwt, principal, authorities);
    }
}
