package com.manhpham.auth.controller;

import com.manhpham.auth.config.JwtKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeys jwtKeys;

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        return jwtKeys.jwkSet();
    }
}
