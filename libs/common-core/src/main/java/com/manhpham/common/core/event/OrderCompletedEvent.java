package com.manhpham.common.core.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload "đơn đã hoàn tất (đã thanh toán)" — CONTRACT dùng chung: Order phát (topic
 * {@code order.events}), Ticket consume để phát vé, Notification consume để gửi vé/email.
 *
 * <p>Đây là SUPERSET các field mọi consumer cần; consumer nào không dùng field nào thì cứ
 * bỏ qua (Jackson khớp theo TÊN FIELD). {@code seatIds} có khi SEATED (phát 1 vé/ghế);
 * rỗng với GA (phát {@code quantity} vé). {@code email} lấy từ claim email của JWT, để
 * Notification gửi xác nhận. Không kèm dữ liệu nhạy cảm.
 */
public record OrderCompletedEvent(
        UUID orderId,
        UUID userId,
        String email,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        List<UUID> seatIds,
        long amountMinor,
        String currency,
        Instant occurredAt) {

    /** Factory tiện dụng (producer dùng): chuẩn hoá {@code seatIds} null → rỗng, đóng dấu thời điểm. */
    public static OrderCompletedEvent of(UUID orderId, UUID userId, String email, UUID eventId,
                                         UUID ticketTypeId, int quantity, List<UUID> seatIds,
                                         long amountMinor, String currency) {
        return new OrderCompletedEvent(orderId, userId, email, eventId, ticketTypeId, quantity,
                seatIds == null ? List.of() : seatIds, amountMinor, currency, Instant.now());
    }
}
