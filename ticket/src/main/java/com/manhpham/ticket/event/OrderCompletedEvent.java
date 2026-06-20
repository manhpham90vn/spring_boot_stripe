package com.manhpham.ticket.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản SAO phía nhận của event do Order phát (topic {@code order.events}). Mỗi service tự
 * sở hữu model; khớp theo TÊN FIELD khi deserialize JSON (xem outbox-debezium.md §5).
 */
public record OrderCompletedEvent(
        UUID orderId,
        UUID userId,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        long amountMinor,
        String currency,
        Instant occurredAt) {
}
