package com.manhpham.payment.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** Một dòng outbox, ghi CÙNG transaction với thay đổi nghiệp vụ. Debezium đọc INSERT từ WAL. */
@Entity
@Table(name = "outbox")
@Getter
public class OutboxEventEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64, updatable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxEventEntity() {
        // for JPA
    }

    private OutboxEventEntity(String aggregateType, String aggregateId, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public static OutboxEventEntity create(String aggregateType, String aggregateId,
                                           String eventType, String payload) {
        return new OutboxEventEntity(aggregateType, aggregateId, eventType, payload);
    }
}
