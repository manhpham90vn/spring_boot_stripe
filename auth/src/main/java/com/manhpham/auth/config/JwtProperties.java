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
}
