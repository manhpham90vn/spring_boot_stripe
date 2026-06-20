package com.manhpham.order.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload "đơn đã hoàn tất (đã thanh toán)" — để Ticket phát vé, Notification gửi vé.
 * Chỉ field cần cho consumer; không kèm dữ liệu nhạy cảm.
 */
public record OrderCompletedEvent(
        UUID orderId,
        UUID userId,
        String email,        // contact để Notification gửi xác nhận (lấy từ claim email của JWT)
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        long amountMinor,
        String currency,
        Instant occurredAt) {

    public static OrderCompletedEvent of(UUID orderId, UUID userId, String email, UUID eventId,
                                         UUID ticketTypeId, int quantity, long amountMinor, String currency) {
        return new OrderCompletedEvent(orderId, userId, email, eventId, ticketTypeId, quantity,
                amountMinor, currency, Instant.now());
    }
}
