package com.manhpham.auth.config;

import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class JwtKeys {

    private final PrivateKey privateKey;
    private final PublicJwk<RSAPublicKey> publicJwk;
    private final String keyId;

    public JwtKeys(JwtProperties properties, ResourceLoader resourceLoader) {
        // Xác định nguồn khoá theo độ ưu tiên:
        //   fromLocation = có đường dẫn file PEM (cả private lẫn public) → đọc từ file/secret.
        //   inline       = có PEM nhồi thẳng vào property.
        //   configured   = một trong hai → dùng khoá CỐ ĐỊNH; nếu không, tự sinh khoá tạm.
        boolean fromLocation = StringUtils.hasText(properties.getPrivateKeyLocation())
                && StringUtils.hasText(properties.getPublicKeyLocation());
        boolean inline = StringUtils.hasText(properties.getPrivateKey())
                && StringUtils.hasText(properties.getPublicKey());
        boolean configured = fromLocation || inline;

        // Lấy nội dung PEM: ưu tiên đọc từ file (fromLocation), ngược lại lấy chuỗi inline.
        String privateKeyPem = fromLocation
                ? readPem(resourceLoader, properties.getPrivateKeyLocation())
                : properties.getPrivateKey();
        String publicKeyPem = fromLocation
                ? readPem(resourceLoader, properties.getPublicKeyLocation())
                : properties.getPublicKey();

        // Có cấu hình → parse cặp khoá từ PEM. Không có gì → SINH khoá RSA tạm (xem cảnh báo dưới).
        KeyPair keyPair = configured
                ? loadFromPem(privateKeyPem, publicKeyPem)
                : generateRsaKeyPair();

        this.privateKey = keyPair.getPrivate();
        // Bọc public key thành JWK. idFromThumbprint() sinh kid = băm (thumbprint) của
        // chính public key → kid mang tính tất định: cùng một key luôn ra cùng một kid.
        // Nhờ vậy kid gắn vào token khi ký (JwtServiceImpl) chắc chắn khớp kid trong JWKS,
        // không cần tự đặt tên/đồng bộ thủ công.
        this.publicJwk = Jwks.builder()
                .key((RSAPublicKey) keyPair.getPublic())
                .idFromThumbprint()
                .build();
        this.keyId = publicJwk.getId();

        if (configured) {
            log.info("Loaded RSA signing key from configuration (kid={}).", keyId);
        } else {
            log.warn("Generated EPHEMERAL RSA signing key (kid={}). Tokens reset on restart; "
                    + "set auth.jwt.private-key/public-key from a K8s Secret before running "
                    + "multiple replicas.", keyId);
        }
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    /** Public key RSA để verify chính token của ta (nếu Auth tự làm resource-server). */
    public RSAPublicKey getPublicKey() {
        return publicJwk.toKey();
    }

    public String getKeyId() {
        return keyId;
    }

    /** Thân tài liệu JWKS: {@code {"keys": [ <public jwk> ]}} — JwksController trả ra đây. */
    public Map<String, Object> jwkSet() {
        return Map.of("keys", List.of(publicJwk));
    }

    private static String readPem(ResourceLoader resourceLoader, String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("JWT key resource not found: " + location);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read JWT key resource: " + location, e);
        }
    }

    private static KeyPair loadFromPem(String privateKeyPem, String publicKeyPem) {
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            PrivateKey priv = rsa.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privateKeyPem)));
            RSAPublicKey pub = (RSAPublicKey) rsa.generatePublic(new X509EncodedKeySpec(decodePem(publicKeyPem)));
            return new KeyPair(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA key pair from auth.jwt.private-key/public-key", e);
        }
    }

    /** Bỏ phần "-----BEGIN/END-----" và khoảng trắng của PEM rồi Base64-decode lấy DER thô. */
    private static byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
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
