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

/** Phát access token ký bằng RSA/RS256 (chữ ký bất đối xứng: ký bằng private, verify bằng public). */
@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JwtKeys keys;
    private final JwtProperties properties;

    @Override
    public IssuedToken issueAccessToken(User user) {
        Instant now = Instant.now();
        // Thời điểm hết hạn = bây giờ + TTL cấu hình. Resource-server sẽ kiểm tra exp này.
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());

        String token = Jwts.builder()
                // kid: id của key đang ký. Gateway/service đọc kid này trong header để
                // tra đúng public key trong JWKS mà verify chữ ký → bắt buộc phải có.
                .header().keyId(keys.getKeyId()).and()
                // iss: ai phát token. Resource-server kiểm tra khớp app.security.issuer.
                .issuer(properties.getIssuer())
                // sub: chủ thể của token = id user (sẽ thành Authentication#getName).
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                // roles: nguồn phân quyền. Resource-server đổi "USER" → authority ROLE_USER.
                .claim("roles", List.of(user.getRole().name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                // Ký bằng PRIVATE key (chỉ Auth giữ). Ai có public key đều verify được,
                // nhưng không ai giả mạo được token vì không có private key. Thuật toán RS256.
                .signWith(keys.getPrivateKey(), Jwts.SIG.RS256)
                .compact();

        // Trả token + số giây còn sống để client biết khi nào cần xin token mới.
        return new IssuedToken(token, Duration.between(now, expiresAt).toSeconds());
    }
}
