package com.manhpham.notification.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản SAO phía nhận của event do Order phát (topic {@code order.events}). Khớp theo TÊN
 * FIELD khi deserialize JSON; chỉ khai field cần để soạn email (field thừa được bỏ qua).
 */
public record OrderCompletedEvent(
        UUID orderId,
        UUID userId,
        String email,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        long amountMinor,
        String currency,
        Instant occurredAt) {
}
