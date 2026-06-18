package com.manhpham.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunables for token issuance. Keys themselves are not configured here yet —
 * an ephemeral RSA pair is generated at startup (see {@link JwtKeys}). When the
 * service moves onto K8s the private key should come from a mounted Secret.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    /** Value placed in the {@code iss} claim and matched by downstream services. */
    private String issuer = "auth";

    /** Lifetime of an issued access token. */
    private Duration accessTokenTtl = Duration.ofHours(1);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
}
