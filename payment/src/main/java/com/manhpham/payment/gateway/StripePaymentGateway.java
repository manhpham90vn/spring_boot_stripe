package com.manhpham.payment.gateway;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cổng thanh toán THẬT qua Stripe (Payment Intents) — hiện thực DUY NHẤT của {@link PaymentGateway}.
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
 * <p>Dùng API Map-based (ổn định) + {@code Idempotency-Key} để timeout/retry KHÔNG tạo hai
 * intent. Tạo PaymentIntent ở đây chỉ KHỞI TẠO giao dịch (status {@code PROCESSING}) — để THẬT
 * SỰ {@code succeeded} cần client xác nhận (Payment Element) và <b>webhook</b> báo về
 * (xem {@code StripeWebhookService} + flows/payment-stripe-flow.md).
 */
@Component
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final String apiKey;

    public StripePaymentGateway(@Value("${stripe.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    @RateLimiter(name = "stripe")
    @Retry(name = "stripe", fallbackMethod = "createIntentFallback")
    public IntentResult createIntent(UUID orderId, long amountMinor, String currency, String idempotencyKey) {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", amountMinor);                 // minor units (JPY: KHÔNG ×100)
        params.put("currency", currency.toLowerCase());
        params.put("automatic_payment_methods", Map.of("enabled", true)); // Stripe tự bật PM hợp lệ
        params.put("metadata", Map.of("order_id", orderId.toString()));    // map đơn ↔ PI ở webhook (2.15)

        RequestOptions options = RequestOptions.builder()
                .setApiKey(apiKey)
                .setIdempotencyKey(idempotencyKey)         // gọi lại không tạo 2 intent (2.18)
                .build();
        try {
            PaymentIntent pi = PaymentIntent.create(params, options);
            // Intent vừa tạo ở requires_payment_method → PROCESSING; chờ client xác nhận + webhook.
            log.info("[STRIPE] PaymentIntent {} status={} order={}", pi.getId(), pi.getStatus(), orderId);
            return new IntentResult(pi.getId(), pi.getClientSecret(), IntentResult.Status.PROCESSING);
        } catch (StripeException e) {
            if (isRetryable(e)) {
                throw new StripeTransientException(e); // @Retry backoff cho lỗi tạm thời
            }
            // Lỗi nghiệp vụ 4xx (invalid request...) — KHÔNG retry.
            log.warn("[STRIPE] business error order={}: {}", orderId, e.getMessage());
            return new IntentResult(null, null, IntentResult.Status.FAILED);
        }
    }

    @Override
    public String retrieveClientSecret(String paymentIntentId) {
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(apiKey).build();
            return PaymentIntent.retrieve(paymentIntentId, options).getClientSecret();
        } catch (StripeException e) {
            log.warn("[STRIPE] retrieve client_secret lỗi PI={}: {}", paymentIntentId, e.getMessage());
            return null;
        }
    }

    @Override
    @RateLimiter(name = "stripe")
    @Retry(name = "stripe") // retry-exceptions=StripeTransientException → chỉ lỗi tạm thời; hết lần thì ném ra
    public String refund(String paymentIntentId, String idempotencyKey) {
        Map<String, Object> params = new HashMap<>();
        params.put("payment_intent", paymentIntentId);

        RequestOptions options = RequestOptions.builder()
                .setApiKey(apiKey)
                .setIdempotencyKey(idempotencyKey)         // gọi lại không hoàn hai lần (payment_issue.md 2.18)
                .build();
        try {
            Refund refund = Refund.create(params, options);
            log.info("[STRIPE] Refund {} PI={} status={}", refund.getId(), paymentIntentId, refund.getStatus());
            return refund.getId();
        } catch (StripeException e) {
            if (isRetryable(e)) {
                throw new StripeTransientException(e); // @Retry backoff cho lỗi tạm thời
            }
            // Lỗi nghiệp vụ 4xx (vd PI chưa succeeded) — KHÔNG retry; ném để saga rollback + thử lại sau.
            log.warn("[STRIPE] refund business error PI={}: {}", paymentIntentId, e.getMessage());
            throw new IllegalStateException("Stripe refund thất bại PI=" + paymentIntentId + ": " + e.getMessage(), e);
        }
    }

    /** Fallback khi HẾT retry (lỗi tạm thời kéo dài) → FAILED; reconciliation phân xử (2.18/3.4). */
    @SuppressWarnings("unused") // gọi bởi Resilience4j qua tên
    private IntentResult createIntentFallback(UUID orderId, long amountMinor, String currency,
                                              String idempotencyKey, Throwable t) {
        log.error("[STRIPE] createIntent fallback (hết retry) order={}: {}", orderId, t.getMessage());
        return new IntentResult(null, null, IntentResult.Status.FAILED);
    }

    /** 429 (rate limit), timeout kết nối, hoặc 5xx → đáng retry. 4xx nghiệp vụ → không. */
    private static boolean isRetryable(StripeException e) {
        return e instanceof RateLimitException
                || e instanceof ApiConnectionException
                || e.getStatusCode() >= 500;
    }
}
