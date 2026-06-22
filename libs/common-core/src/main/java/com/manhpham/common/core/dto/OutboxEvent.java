package com.manhpham.common.core.dto;

/**
 * Lớp CƠ SỞ trừu tượng cho mọi domain event sẽ đi qua transactional outbox.
 *
 * <p>Mỗi event con phải khai 3 mẩu metadata mà Debezium Outbox Event Router dùng để
 * định tuyến lên Kafka:
 * <ul>
 *   <li>{@code aggregateType} — loại thực thể gốc (vd "user"); thường map sang TÊN TOPIC.</li>
 *   <li>{@code aggregateId} — id của thực thể (vd id user); dùng làm KEY để Kafka phân
 *       partition → mọi event cùng một user giữ đúng thứ tự.</li>
 *   <li>{@code eventType} — loại biến cố (vd "UserRegistered"); để consumer phân nhánh xử lý.</li>
 * </ul>
 *
 * <p>{@code payload} là dữ liệu nghiệp vụ (sẽ được serialize JSON ở OutboxEventSender).
 * Dùng generic {@code <T>} để mỗi event con gắn đúng kiểu payload của mình một cách type-safe.
 */
public abstract class OutboxEvent<T> {

    private final T payload;

    protected OutboxEvent(T payload) {
        this.payload = payload;
    }

    public T getPayload() {
        return payload;
    }

    /** Loại aggregate (vd "user") — Debezium thường dùng làm tên topic Kafka. */
    public abstract String getAggregateType();

    /** Id aggregate (vd id user) — dùng làm message key để giữ thứ tự theo từng thực thể. */
    public abstract String getAggregateId();

    /** Loại event (vd "UserRegistered") — giúp consumer biết đây là biến cố gì. */
    public abstract String getEventType();
}
