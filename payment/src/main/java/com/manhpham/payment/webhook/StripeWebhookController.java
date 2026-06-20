package com.manhpham.payment.webhook;

import com.stripe.exception.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint nhận webhook Stripe. Vào THẲNG service này qua nginx/Ingress (reverse proxy DMZ),
 * BỎ QUA apigateway — xác thực bằng CHỮ KÝ Stripe, KHÔNG phải JWT (xem API-CONVENTIONS.md §5).
 *
 * <p>Trả 2xx NHANH: Stripe coi non-2xx là thất bại và retry. Body lấy dạng chuỗi THÔ để
 * verify chữ ký đúng trên đúng bytes (đừng để framework parse/biến đổi trước khi verify).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        try {
            webhookService.handle(payload, signature);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            // Chữ ký sai/giả mạo/replay quá hạn → 400, không xử lý.
            log.warn("Webhook chữ ký không hợp lệ: {}", e.getMessage());
            return ResponseEntity.badRequest().body("invalid signature");
        }
    }
}
