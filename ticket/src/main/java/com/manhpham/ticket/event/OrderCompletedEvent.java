package com.manhpham.ticket.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bản SAO phía nhận của event do Order phát (topic {@code order.events}). Mỗi service tự
 * sở hữu model; khớp theo TÊN FIELD khi deserialize JSON (xem outbox-debezium.md §5).
 * {@code seatIds} có khi SEATED → phát 1 vé/ghế; rỗng với GA → phát {@code quantity} vé.
 */
public record OrderCompletedEvent(
        UUID orderId,
        UUID userId,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        List<UUID> seatIds,
        long amountMinor,
        String currency,
        Instant occurredAt) {
}
