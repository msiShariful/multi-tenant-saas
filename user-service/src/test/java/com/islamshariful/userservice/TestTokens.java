package com.islamshariful.userservice;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Mints tokens exactly as auth-service does, signed with a key pair generated for the test run.
 *
 * <p>The alternative — pointing the tests at a live auth-service — would make this service's suite fail
 * whenever the other service is broken or simply not running, which is precisely the coupling that
 * publishing a JWKS endpoint was meant to avoid. What matters is that the <em>claim contract</em> holds,
 * and that is reproduced here exactly: same claim names, same issuer, same audience.
 *
 * <p>The {@link JwtDecoder} bean replaces the one Boot would build from {@code jwk-set-uri}, so nothing
 * reaches the network.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestTokens {

    static final String ISSUER = "http://auth-service.test";
    static final String AUDIENCE = "tenantbase-api-test";
    private static final String KEY_ID = "test-key";

    private final RSAKey signingKey;

    public TestTokens() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KEY_ID)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate a test signing key", ex);
        }
    }

    @Bean
    JwtDecoder jwtDecoder() throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(ISSUER), new JwtAudienceValidator(AUDIENCE)));
        return decoder;
    }

    @Bean
    TokenMinter tokenMinter() {
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(signingKey));
        return new TokenMinter(new NimbusJwtEncoder(source));
    }

    /**
     * Produces the same claim set auth-service signs.
     *
     * <p>Not a {@code @Component}: test sources are on the classpath during a test run, so component
     * scanning would pick it up and try to autowire a {@code JwtEncoder} bean that does not exist -- the
     * encoder is built inline by {@link #tokenMinter()} from the generated key.
     */
    public static class TokenMinter {

        private final JwtEncoder encoder;

        TokenMinter(JwtEncoder encoder) {
            this.encoder = encoder;
        }

        public String mint(UUID userId, UUID tenantId, String tenantSlug, String email, String... roles) {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(ISSUER)
                    .audience(List.of(AUDIENCE))
                    .subject(userId.toString())
                    .id(UUID.randomUUID().toString())
                    .issuedAt(now)
                    .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                    .claim("tenant_id", tenantId.toString())
                    .claim("tenant_slug", tenantSlug)
                    .claim("email", email)
                    .claim("roles", List.of(roles))
                    .build();
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
            return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }

        /** A token missing a claim this service requires, to prove malformed tokens are 401 not 500. */
        public String mintWithoutTenant(UUID userId, String email) {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(ISSUER)
                    .audience(List.of(AUDIENCE))
                    .subject(userId.toString())
                    .issuedAt(now)
                    .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                    .claim("email", email)
                    .build();
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
            return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }
    }
}
