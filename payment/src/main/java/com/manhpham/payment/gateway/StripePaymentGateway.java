package com.manhpham.payment.gateway;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cổng thanh toán THẬT qua Stripe (Payment Intents). Bật bằng {@code payment.gateway=stripe}.
 *
 * <p><b>Tôn trọng giới hạn Stripe (~100–200 req/s):</b>
 * <ul>
 *   <li>{@code @RateLimiter("stripe")} — chặn vượt ngưỡng tại nguồn (cấu hình ở
 *       application.properties: limit-for-period/refresh-period).</li>
 *   <li>{@code @Retry("stripe")} — thử lại với <b>exponential backoff</b> CHỈ với lỗi đáng
 *       retry (429/timeout/5xx, bọc qua {@link StripeTransientException}); lỗi nghiệp vụ
 *       4xx (thẻ bị từ chối) KHÔNG retry. Hết lần → {@code chargeFallback}.</li>
 * </ul>
 *
 * <p>Dùng API Map-based (ổn định) + {@code Idempotency-Key} để timeout/retry KHÔNG thu hai
 * lần. LƯU Ý (walking skeleton): tạo PaymentIntent ở đây chỉ KHỞI TẠO giao dịch — để THẬT
 * SỰ {@code succeeded} cần client xác nhận (Payment Element) và <b>webhook</b> báo về
 * (xem {@code WebhookController} + payment-stripe-flow.md). Luồng e2e dev dùng {@link MockPaymentGateway}.
 */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "stripe")
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final String apiKey;

    public StripePaymentGateway(@Value("${stripe.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    @RateLimiter(name = "stripe")
    @Retry(name = "stripe", fallbackMethod = "chargeFallback")
    public ChargeOutcome charge(UUID orderId, long amountMinor, String currency, String idempotencyKey) {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", amountMinor);                 // minor units (JPY: KHÔNG ×100)
        params.put("currency", currency.toLowerCase());
        params.put("metadata", Map.of("order_id", orderId.toString())); // liên kết đơn ↔ PI (2.15)

        RequestOptions options = RequestOptions.builder()
                .setApiKey(apiKey)
                .setIdempotencyKey(idempotencyKey)         // chống thu hai lần (2.18)
                .build();
        try {
            PaymentIntent pi = PaymentIntent.create(params, options);
            ChargeOutcome.Status status = "succeeded".equals(pi.getStatus())
                    ? ChargeOutcome.Status.SUCCEEDED
                    : ChargeOutcome.Status.FAILED; // thường requires_payment_method → chờ webhook xác nhận
            log.info("[STRIPE] PaymentIntent {} status={} order={}", pi.getId(), pi.getStatus(), orderId);
            return new ChargeOutcome(pi.getId(), status);
        } catch (StripeException e) {
            if (isRetryable(e)) {
                // Ném ra để @Retry thử lại với backoff (không nuốt lỗi tạm thời).
                throw new StripeTransientException(e);
            }
            // Lỗi nghiệp vụ 4xx (card declined, invalid request) — KHÔNG retry.
            log.warn("[STRIPE] business error order={}: {}", orderId, e.getMessage());
            return new ChargeOutcome(null, ChargeOutcome.Status.FAILED);
        }
    }

    /**
     * Fallback khi đã HẾT số lần retry (lỗi tạm thời kéo dài). Charge nghi "mồ côi" (timeout
     * không rõ kết cục) → để FAILED; reconciliation định kỳ sẽ phân xử (payment_issue.md 2.18/3.4).
     */
    @SuppressWarnings("unused") // gọi bởi Resilience4j qua tên
    private ChargeOutcome chargeFallback(UUID orderId, long amountMinor, String currency,
                                         String idempotencyKey, Throwable t) {
        log.error("[STRIPE] charge fallback (hết retry) order={}: {}", orderId, t.getMessage());
        return new ChargeOutcome(null, ChargeOutcome.Status.FAILED);
    }

    /** 429 (rate limit), timeout kết nối, hoặc 5xx → đáng retry. 4xx nghiệp vụ → không. */
    private static boolean isRetryable(StripeException e) {
        return e instanceof RateLimitException
                || e instanceof ApiConnectionException
                || e.getStatusCode() >= 500;
    }
}
