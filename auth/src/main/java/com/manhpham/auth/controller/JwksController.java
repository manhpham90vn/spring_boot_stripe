package com.manhpham.auth.controller;

import com.manhpham.auth.config.JwtKeys;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the public signing key as a JWK Set. The API gateway is configured
 * with {@code jwk-set-uri=http://auth:8081/oauth2/jwks} and calls this directly
 * over internal DNS (not through itself) to verify token signatures.
 */
@RestController
public class JwksController {

    private final JwtKeys jwtKeys;

    public JwksController(JwtKeys jwtKeys) {
        this.jwtKeys = jwtKeys;
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        return jwtKeys.jwkSet();
    }
}
