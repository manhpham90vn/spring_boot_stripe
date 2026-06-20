package com.manhpham.order.event;

import com.manhpham.order.core.dto.OutboxEvent;

/** Bọc {@link OrderCompletedEvent} thành bản ghi outbox: topic {@code order.events}, key = orderId. */
public final class OrderCompletedOutboxEvent extends OutboxEvent<OrderCompletedEvent> {

    private OrderCompletedOutboxEvent(OrderCompletedEvent payload) {
        super(payload);
    }

    public static OrderCompletedOutboxEvent of(OrderCompletedEvent payload) {
        return new OrderCompletedOutboxEvent(payload);
    }

    @Override
    public String getAggregateType() {
        return "order"; // → topic order.events
    }

    @Override
    public String getAggregateId() {
        return getPayload().orderId().toString(); // key → giữ thứ tự theo đơn
    }

    @Override
    public String getEventType() {
        return "OrderCompleted";
    }
}
