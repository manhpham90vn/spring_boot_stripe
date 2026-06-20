package com.manhpham.order.core.dto;

/**
 * Lớp cơ sở cho domain event đi qua transactional outbox (xem outbox-debezium.md).
 * Mỗi event con khai 3 metadata định tuyến: aggregateType (→ topic), aggregateId (→ key),
 * eventType (discriminator).
 */
public abstract class OutboxEvent<T> {

    private final T payload;

    protected OutboxEvent(T payload) {
        this.payload = payload;
    }

    public T getPayload() {
        return payload;
    }

    public abstract String getAggregateType();

    public abstract String getAggregateId();

    public abstract String getEventType();
}
