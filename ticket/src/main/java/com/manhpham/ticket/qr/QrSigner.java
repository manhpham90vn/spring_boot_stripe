package com.manhpham.ticket.qr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * Ký số QR token bằng HMAC-SHA256. Token = {@code "<ticketId>.<chữ_ký>"}. Khi quét tại cổng,
 * verify lại chữ ký bằng cùng secret → không ai chế được vé giả nếu không có secret.
 *
 * <p>Dev dùng secret mặc định; prod nạp {@code ticket.qr.secret} từ K8s Secret. (Bản nâng
 * cấp có thể đổi sang ký bất đối xứng RS256 như JWT để cổng chỉ cần public key.)
 */
@Component
public class QrSigner {

    private static final String ALGO = "HmacSHA256";
    private final byte[] secret;

    public QrSigner(@Value("${ticket.qr.secret:dev-qr-secret-change-me}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Tạo token đã ký cho một vé. */
    public String sign(UUID ticketId) {
        return ticketId + "." + hmac(ticketId.toString());
    }

    /** Verify chữ ký của token (so sánh hằng-thời-gian chống timing attack). */
    public boolean verify(String token) {
        int dot = token.lastIndexOf('.');
        if (dot <= 0) {
            return false;
        }
        String id = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        return MessageDigest.isEqual(
                sig.getBytes(StandardCharsets.UTF_8),
                hmac(id).getBytes(StandardCharsets.UTF_8));
    }

    /** Lấy ticketId từ token (chỉ gọi sau khi verify thành công). */
    public UUID ticketId(String token) {
        return UUID.fromString(token.substring(0, token.lastIndexOf('.')));
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret, ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được HMAC cho QR", e);
        }
    }
}
