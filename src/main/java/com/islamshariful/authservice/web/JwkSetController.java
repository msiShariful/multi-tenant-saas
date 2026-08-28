package com.islamshariful.authservice.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the public half of the signing key.
 *
 * <p>This endpoint is what lets the rest of the estate validate tokens on its own. user-service and the
 * gateway point {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} here, cache the key, and verify
 * signatures locally — no call back to auth-service on the request path, so this service is not a runtime
 * dependency of every other one, and rotating keys does not require redeploying anybody.
 *
 * <p>Public by design: it contains a public key. {@code toJSONObject(true)} restricts the output to public
 * key parameters, so the private exponent cannot leak here even if the bean were replaced with a full key.
 */
@RestController
@Tag(name = "Keys", description = "Public signing key material")
public class JwkSetController {

    private final RSAKey signingKey;

    public JwkSetController(RSAKey signingKey) {
        this.signingKey = signingKey;
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "JSON Web Key Set",
            description = "RFC 7517 key set used by downstream services to verify access tokens offline.")
    public Map<String, Object> jwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject(true);
    }
}
