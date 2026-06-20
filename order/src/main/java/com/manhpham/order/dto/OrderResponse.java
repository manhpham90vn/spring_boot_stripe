package com.manhpham.order.dto;

import com.manhpham.order.entities.Order;
import com.manhpham.order.entities.OrderStatus;

import java.util.UUID;

/** Đơn trả về client. Không lộ userId (đã kiểm chủ sở hữu ở tầng service). */
public record OrderResponse(
        UUID id,
        OrderStatus status,
        UUID eventId,
        UUID ticketTypeId,
        int quantity,
        long amountMinor,
        String currency,
        UUID paymentId,
        String failureReason) {

    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getStatus(), o.getEventId(), o.getTicketTypeId(),
                o.getQuantity(), o.getAmountMinor(), o.getCurrency(), o.getPaymentId(), o.getFailureReason());
    }
}
