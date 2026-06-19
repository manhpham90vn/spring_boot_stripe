package com.manhpham.auth.services.impl;

import com.manhpham.auth.config.JwtKeys;
import com.manhpham.auth.config.JwtProperties;
import com.manhpham.auth.entities.User;
import com.manhpham.auth.services.JwtService;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Issues signed RS256 access tokens. */
@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JwtKeys keys;
    private final JwtProperties properties;

    @Override
    public IssuedToken issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());

        String token = Jwts.builder()
                .header().keyId(keys.getKeyId()).and()
                .issuer(properties.getIssuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(keys.getPrivateKey(), Jwts.SIG.RS256)
                .compact();

        return new IssuedToken(token, Duration.between(now, expiresAt).toSeconds());
    }
}
