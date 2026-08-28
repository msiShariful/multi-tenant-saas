package com.islamshariful.authservice.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Asymmetric signing key material and the encoder/decoder built on top of it.
 *
 * <p><b>Why RSA rather than a shared HMAC secret.</b> In a microservice estate the auth-service is the only
 * component that should be able to <em>mint</em> tokens, while every other component needs to
 * <em>verify</em> them. A symmetric secret gives both powers to whoever holds it, so a read-only service
 * that leaks its config can forge admin tokens for any tenant. With RS256 the private key never leaves this
 * service and peers fetch the public half from {@code /.well-known/jwks.json} — no shared secret, and no
 * per-request call back here to validate.
 */
@Configuration(proxyBeanMethods = false)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);
    private static final int DEVELOPMENT_KEY_SIZE = 2048;

    @Bean
    public RSAKey signingKey(JwtProperties properties) {
        KeyPairSource source = properties.hasConfiguredKeyPair()
                ? loadConfiguredKeyPair(properties)
                : generateDevelopmentKeyPair();
        return new RSAKey.Builder(source.publicKey())
                .privateKey(source.privateKey())
                .keyID(properties.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
    }

    /**
     * Exposed as a bean so the JWKS controller and the encoder share one instance; {@link ImmutableJWKSet}
     * means key rotation is a restart. A rotating {@code JWKSource} backed by a key store is the next step
     * once there is somewhere to store keys.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey signingKey) {
        return new ImmutableJWKSet<>(new JWKSet(signingKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * This service validates its own tokens because it is also a resource server (for {@code /me}, admin
     * user management, and logout).
     *
     * <p>Issuer <em>and</em> audience are both checked. Signature alone is not enough: a token this service
     * signed for a different audience — say a future internal service-to-service token — must not be
     * accepted here just because the signature verifies.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties properties) throws java.security.KeyStoreException {
        RSAPublicKey publicKey;
        try {
            publicKey = signingKey.toRSAPublicKey();
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException("Configured signing key does not expose an RSA public key", ex);
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(properties.issuer()), new JwtAudienceValidator(properties.audience())));
        return decoder;
    }

    private KeyPairSource loadConfiguredKeyPair(JwtProperties properties) {
        try (InputStream privateKeyStream = properties.privateKeyLocation().getInputStream();
                InputStream publicKeyStream = properties.publicKeyLocation().getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateKeyStream);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicKeyStream);
            log.info("Loaded RSA signing key '{}' from configured PEM locations", properties.keyId());
            return new KeyPairSource(publicKey, privateKey);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read the configured RSA key pair", ex);
        }
    }

    /**
     * Keeps {@code docker compose up} a single command with no key ceremony. The consequence is stated
     * loudly because it matters: every restart invalidates outstanding access tokens, and two replicas would
     * each sign with a different key. Refresh tokens survive — they are opaque rows, not signed material.
     */
    private KeyPairSource generateDevelopmentKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(DEVELOPMENT_KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            log.warn(
                    """

                    ****************************************************************************
                    No RSA key pair configured; generated an ephemeral one for this JVM.
                    Access tokens will not survive a restart and replicas will not agree.
                    Set tenantbase.security.jwt.private-key-location / public-key-location
                    before running anywhere but a development machine. See README 'Signing keys'.
                    ****************************************************************************""");
            return new KeyPairSource((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA key pair generation is unavailable on this JVM", ex);
        }
    }

    private record KeyPairSource(RSAPublicKey publicKey, RSAPrivateKey privateKey) {}
}
