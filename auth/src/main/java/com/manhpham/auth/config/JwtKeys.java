package com.manhpham.auth.config;

import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;

/**
 * Holds the RSA key pair used to sign access tokens and exposes the public half
 * as a JWK so the API gateway (an OAuth2 resource server) can verify signatures
 * via {@code /oauth2/jwks}.
 *
 * <p>The pair is generated in-memory at startup. This is fine for a single-replica
 * dev/walking-skeleton setup: the gateway fetches the JWKS dynamically and caches
 * it. It is NOT production-ready — multiple replicas would each expose a different
 * key, and tokens are invalidated on every restart. Externalize the key into a
 * K8s Secret before scaling out.
 */
@Component
public class JwtKeys {

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);

    private final PrivateKey privateKey;
    private final PublicJwk<RSAPublicKey> publicJwk;
    private final String keyId;

    public JwtKeys() {
        KeyPair keyPair = generateRsaKeyPair();
        this.privateKey = keyPair.getPrivate();
        // idFromThumbprint() derives a stable kid from the key material (RFC 7638).
        this.publicJwk = Jwks.builder()
                .key((RSAPublicKey) keyPair.getPublic())
                .idFromThumbprint()
                .build();
        this.keyId = publicJwk.getId();
        log.warn("Generated EPHEMERAL RSA signing key (kid={}). Tokens reset on restart; "
                + "externalize via a K8s Secret before running multiple replicas.", keyId);
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getKeyId() {
        return keyId;
    }

    /** JWKS document body: {@code {"keys": [ <public jwk> ]}}. */
    public Map<String, Object> jwkSet() {
        return Map.of("keys", List.of(publicJwk));
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation not available", e);
        }
    }
}
