package com.manhpham.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình JWT của Auth (prefix {@code auth.jwt}). Về nguồn cặp khoá ký, JwtKeys ưu
 * tiên theo thứ tự: (1) *-location (đọc PEM từ file/secret) → (2) *-key inline →
 * (3) nếu cả hai trống thì TỰ SINH khoá tạm (chỉ hợp dev 1 replica; token reset mỗi
 * lần restart). Production phải nạp khoá cố định từ K8s Secret.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    /** Giá trị claim {@code iss} gắn vào token; resource-server các bên kiểm khớp đúng chuỗi này. */
    private String issuer = "auth";

    /** Thời hạn sống của access token. Ngắn để giảm rủi ro nếu lộ; dài để đỡ phải login lại. */
    private Duration accessTokenTtl = Duration.ofHours(1);

    /** Private key PEM dạng inline (vd nhồi thẳng từ Secret). Ký token bằng khoá này. */
    private String privateKey = "";

    /** Public key PEM dạng inline. Đem verify token + publish qua JWKS. */
    private String publicKey = "";

    /** Vị trí Spring Resource của private key PEM (PKCS#8), vd {@code file:/keys/jwt-private.pem}. */
    private String privateKeyLocation = "";

    /** Vị trí Spring Resource của public key PEM (X.509), vd {@code file:/keys/jwt-public.pem}. */
    private String publicKeyLocation = "";
}
