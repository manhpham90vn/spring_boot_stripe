package com.manhpham.auth.controller;

import com.manhpham.auth.config.JwtKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Công bố JWKS — tập PUBLIC key của Auth dưới dạng chuẩn JSON Web Key Set.
 *
 * <p>Đây là điểm khớp với phía verify: gateway và mọi service hạ nguồn gọi endpoint
 * này (qua {@code jwk-set-uri}) để lấy public key rồi tự kiểm chữ ký JWT, KHÔNG cần
 * hỏi Auth từng request. Chỉ lộ public key (an toàn để công khai), private key dùng
 * để ký không bao giờ ra khỏi Auth. Vì thế endpoint này được permitAll trong SecurityConfig.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeys jwtKeys;

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        // Trả {"keys":[ <public jwk có kid> ]}. kid ở đây khớp kid Auth gắn vào mỗi token.
        return jwtKeys.jwkSet();
    }
}
