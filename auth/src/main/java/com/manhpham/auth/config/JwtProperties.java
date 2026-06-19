package com.manhpham.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    private String issuer = "auth";

    private Duration accessTokenTtl = Duration.ofHours(1);

    private String privateKey = "";

    private String publicKey = "";

    /** Spring Resource location of the PKCS#8 private key PEM, e.g. {@code file:/keys/jwt-private.pem}. */
    private String privateKeyLocation = "";

    /** Spring Resource location of the X.509 public key PEM, e.g. {@code file:/keys/jwt-public.pem}. */
    private String publicKeyLocation = "";
}
