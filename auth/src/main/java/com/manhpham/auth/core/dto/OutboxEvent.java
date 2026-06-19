package com.manhpham.auth.core.dto;

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
