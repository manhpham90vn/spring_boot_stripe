package com.manhpham.payment.event;

import com.manhpham.payment.core.dto.OutboxEvent;

/** Bọc {@link PaymentSettledEvent} thành outbox: topic {@code payment.events}, key = orderId. */
public final class PaymentSettledOutboxEvent extends OutboxEvent<PaymentSettledEvent> {

    private PaymentSettledOutboxEvent(PaymentSettledEvent payload) {
        super(payload);
    }

    public static PaymentSettledOutboxEvent of(PaymentSettledEvent payload) {
        return new PaymentSettledOutboxEvent(payload);
    }

    @Override
    public String getAggregateType() {
        return "payment"; // → topic payment.events
    }

    @Override
    public String getAggregateId() {
        return getPayload().orderId().toString(); // key theo orderId → Order correlate + giữ thứ tự
    }

    @Override
    public String getEventType() {
        return "PaymentSettled";
    }
}
