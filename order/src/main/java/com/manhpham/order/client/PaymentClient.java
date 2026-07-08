package com.manhpham.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/** Gọi API NỘI BỘ của Payment ({@code /internal/**}) qua DNS để tạo PaymentIntent / tra trạng thái. */
@Component
public class PaymentClient {

    private final RestClient http;

    public PaymentClient(RestClient.Builder builder, @Value("${services.payment.url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    /** Tạo PaymentIntent cho đơn → trả clientSecret (cho FE) + trạng thái (PROCESSING|FAILED). */
    public IntentResult createIntent(UUID orderId, long amountMinor, String currency) {
        return http.post()
                .uri("/internal/payment-intents")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("orderId", orderId, "amountMinor", amountMinor, "currency", currency))
                .retrieve()
                .body(IntentResult.class);
    }

    /** Tra trạng thái thanh toán của đơn — cho reconciliation khi event payment.events bị mất. */
    public ChargeResult byOrder(UUID orderId) {
        return http.get().uri("/internal/payment-intents/by-order/{id}", orderId).retrieve().body(ChargeResult.class);
    }

    /** Hoàn tiền cho đơn (saga bù trừ §4: đã thu tiền nhưng hết vé). Idempotent theo orderId. */
    public ChargeResult refund(UUID orderId) {
        return http.post()
                .uri("/internal/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("orderId", orderId))
                .retrieve()
                .body(ChargeResult.class);
    }

    /**
     * HỦY PaymentIntent của đơn (saga bù trừ §4: thanh toán thất bại, Order bỏ cuộc) — gọi
     * TRƯỚC khi nhả chỗ. Idempotent theo orderId. Ném
     * {@link org.springframework.web.client.HttpClientErrorException.Conflict} (409) nếu tiền
     * đã/đang được thu — caller phải GIỮ đơn ở AWAITING_PAYMENT chờ webhook chốt.
     */
    public ChargeResult cancelIntent(UUID orderId) {
        return http.post()
                .uri("/internal/cancellations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("orderId", orderId))
                .retrieve()
                .body(ChargeResult.class);
    }

    /** Kết quả tạo intent. status: PROCESSING (chờ webhook) | FAILED (lỗi tạo, cần bù trừ). */
    public record IntentResult(UUID paymentId, String stripePiId, String clientSecret, String status) {
        public boolean failed() {
            return "FAILED".equals(status);
        }
    }

    /** Trạng thái thanh toán (reconciliation). status: PENDING|PROCESSING|SUCCEEDED|FAILED|CANCELED. */
    public record ChargeResult(UUID paymentId, UUID orderId, String status, String reference, String stripePiId) {
        public boolean succeeded() {
            return "SUCCEEDED".equals(status);
        }
    }
}
