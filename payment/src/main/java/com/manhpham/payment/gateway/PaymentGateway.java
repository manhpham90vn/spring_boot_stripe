package com.manhpham.payment.gateway;

import java.util.UUID;

/**
 * Trừu tượng cổng thanh toán Stripe (một tài khoản, Payment Intents). Tạo PaymentIntent rồi
 * client xác nhận qua Payment Element; kết quả thật chốt từ WEBHOOK (xem
 * impl/01-payment-real-stripe.md). KHÔNG mock — chỉ {@link StripePaymentGateway}.
 */
public interface PaymentGateway {

    /**
     * Tạo PaymentIntent cho một đơn (chưa thu tiền — chờ client xác nhận). {@code idempotencyKey}
     * (= "order:&lt;orderId&gt;") đảm bảo timeout/retry không tạo hai intent (payment_issue.md 2.2/2.18).
     * amount theo minor units.
     */
    IntentResult createIntent(UUID orderId, long amountMinor, String currency, String idempotencyKey);

    /** Lấy lại client_secret của một PaymentIntent đã tạo (cho idempotent hit / resume). */
    String retrieveClientSecret(String paymentIntentId);

    /**
     * Hoàn tiền TOÀN PHẦN cho một PaymentIntent đã succeeded — bù trừ saga khi đã thu tiền nhưng
     * không chốt được SOLD (hết vé, saga-purchase-flow.md §4). {@code idempotencyKey}
     * (= "refund:order:&lt;orderId&gt;") đảm bảo timeout/retry KHÔNG hoàn hai lần. Trả id refund
     * của Stripe (lưu làm {@code paymentRef}).
     */
    String refund(String paymentIntentId, String idempotencyKey);

    /** Kết quả tạo intent: id PaymentIntent + client_secret (cho FE xác nhận) + trạng thái. */
    record IntentResult(String paymentIntentId, String clientSecret, Status status) {
        // PROCESSING: đã tạo intent, chờ webhook. FAILED: lỗi nghiệp vụ khi tạo (không retry được).
        public enum Status { PROCESSING, FAILED }
    }
}
