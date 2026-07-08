package com.manhpham.common.core.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload "thanh toán đã ngã ngũ" (SUCCEEDED/FAILED/CANCELED) — CONTRACT dùng chung: Payment
 * phát (topic {@code payment.events}), Order/saga consume để TIẾP TỤC: SUCCEEDED → chốt SOLD
 * + PAID + phát vé; FAILED/CANCELED → hủy PI (nếu chưa) + nhả chỗ + PAYMENT_FAILED.
 *
 * <p>{@code status} là chuỗi của PaymentStatus ("SUCCEEDED" | "FAILED" | "CANCELED");
 * chỉ SUCCEEDED là thành công (xem {@link #succeeded()}). Đồng bộ thẻ lẫn bất đồng bộ
 * (Konbini/Furikomi) đều đi qua đây.
 */
public record PaymentSettledEvent(
        UUID orderId,
        UUID paymentId,
        String status,
        String reference,
        Instant occurredAt) {

    /** Factory tiện dụng (producer dùng): tự đóng dấu thời điểm xảy ra. */
    public static PaymentSettledEvent of(UUID orderId, UUID paymentId, String status, String reference) {
        return new PaymentSettledEvent(orderId, paymentId, status, reference, Instant.now());
    }

    /** Consumer dùng: chỉ SUCCEEDED là thành công. */
    public boolean succeeded() {
        return "SUCCEEDED".equals(status);
    }
}
