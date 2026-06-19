package com.manhpham.auth.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in the transactional outbox. Written in the SAME transaction as the
 * business change; nothing in the app publishes it — a Debezium connector reads
 * the table's INSERTs from the Postgres WAL and routes them to Kafka. Hence no
 * {@code published_at}/locking columns: the WAL is the source of truth for the
 * publisher, the table is just the transactional handoff (and is purged later).
 *
 * <p>Column names match the Debezium Outbox Event Router field mapping.
 */
@Entity
@Table(name = "outbox")
@Getter // chỉ sinh getter, giữ entity bất biến (tạo qua factory create(...))
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
