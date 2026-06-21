package com.manhpham.payment.entities;

/**
 * Trạng thái thanh toán. Luồng Stripe thật bất đồng bộ: tạo PaymentIntent → {@code PROCESSING}
 * (chờ client xác nhận + webhook) → chốt {@code SUCCEEDED}/{@code FAILED}/{@code CANCELED} từ
 * webhook (nguồn sự thật). Xem flows/payment-stripe-flow.md §6, impl/01-payment-real-stripe.md §4.
 */
public enum PaymentStatus {
    PENDING,      // vừa tạo bản ghi, chưa có PaymentIntent
    PROCESSING,   // đã tạo PaymentIntent, chờ client xác nhận + webhook
    SUCCEEDED,    // webhook xác nhận đã thu tiền
    FAILED,       // tạo intent lỗi nghiệp vụ / webhook payment_failed
    CANCELED,     // PaymentIntent bị huỷ
    REFUNDED      // đã hoàn tiền (bù trừ saga: đã thu nhưng hết vé — saga-purchase-flow.md §4)
}
