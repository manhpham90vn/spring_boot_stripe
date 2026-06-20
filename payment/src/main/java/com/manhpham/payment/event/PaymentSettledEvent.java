package com.manhpham.payment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload "thanh toán đã ngã ngũ" (SUCCEEDED/FAILED) — để Order/saga TIẾP TỤC (đồng bộ thẻ
 * lẫn bất đồng bộ Konbini/Furikomi đều đi qua đây). status là chuỗi của PaymentStatus.
 */
public record PaymentSettledEvent(
        UUID orderId,
        UUID paymentId,
        String status,
        String reference,
        Instant occurredAt) {

    public static PaymentSettledEvent of(UUID orderId, UUID paymentId, String status, String reference) {
        return new PaymentSettledEvent(orderId, paymentId, status, reference, Instant.now());
    }
}
