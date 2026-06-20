package com.manhpham.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/** Gọi API NỘI BỘ của Payment ({@code /internal/charges}) qua DNS để thu tiền. */
@Component
public class PaymentClient {

    private final RestClient http;

    public PaymentClient(RestClient.Builder builder, @Value("${services.payment.url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public ChargeResult charge(UUID orderId, long amountMinor, String currency) {
        return http.post()
                .uri("/internal/charges")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("orderId", orderId, "amountMinor", amountMinor, "currency", currency))
                .retrieve()
                .body(ChargeResult.class);
    }

    /** Tra trạng thái thanh toán của đơn — cho reconciliation khi event payment.events bị mất. */
    public ChargeResult byOrder(UUID orderId) {
        return http.get().uri("/internal/payments/by-order/{id}", orderId).retrieve().body(ChargeResult.class);
    }

    /** status: PENDING | SUCCEEDED | FAILED (chuỗi từ Payment service). */
    public record ChargeResult(UUID paymentId, UUID orderId, String status, String reference) {
        public boolean succeeded() {
            return "SUCCEEDED".equals(status);
        }
    }
}
