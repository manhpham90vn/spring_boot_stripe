package com.manhpham.order.dto;

import com.manhpham.order.entities.Order;
import com.manhpham.order.entities.OrderStatus;

import java.util.UUID;

/**
 * Đơn trả về client. Không lộ userId (đã kiểm chủ sở hữu ở tầng service). {@code clientSecret}
 * CHỈ có ở response của POST /api/order (để FE xác nhận Payment Element) — không trả lại ở GET.
 */
public record OrderResponse(
        UUID id,
        OrderStatus status,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        long amountMinor,
        String currency,
        UUID paymentId,
        String clientSecret,
        String failureReason) {

    /** Dùng cho GET và các nhánh lỗi (REJECTED/PAYMENT_FAILED) — không kèm clientSecret. */
    public static OrderResponse from(Order o) {
        return withSecret(o, null);
    }

    /** Dùng cho POST khi đã tạo PaymentIntent — kèm clientSecret cho FE. */
    public static OrderResponse withSecret(Order o, String clientSecret) {
        return new OrderResponse(o.getId(), o.getStatus(), o.getEventId(), o.getTicketTypeId(),
                o.getQuantity(), o.getAmountMinor(), o.getCurrency(), o.getPaymentId(), clientSecret,
                o.getFailureReason());
    }
}
