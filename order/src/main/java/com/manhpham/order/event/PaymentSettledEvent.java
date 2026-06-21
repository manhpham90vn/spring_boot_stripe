package com.manhpham.order.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản SAO phía nhận của event do Payment phát (topic {@code payment.events}). Order dùng để
 * TIẾP TỤC saga: SUCCEEDED → chốt SOLD + PAID + phát vé; FAILED → nhả chỗ + PAYMENT_FAILED.
 */
public record PaymentSettledEvent(
        UUID orderId,
        UUID paymentId,
        String status,   // "SUCCEEDED" | "FAILED" | "CANCELED" (chỉ SUCCEEDED là thành công)
        String reference,
        Instant occurredAt) {

    public boolean succeeded() {
        return "SUCCEEDED".equals(status);
    }
}
