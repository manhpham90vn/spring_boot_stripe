package com.manhpham.auth.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Một DÒNG trong bảng transactional outbox. Được ghi trong CÙNG transaction với thay đổi
 * nghiệp vụ; KHÔNG có thành phần nào trong app tự đẩy nó đi — một connector Debezium đọc
 * các bản ghi INSERT của bảng này từ WAL của Postgres rồi định tuyến lên Kafka.
 *
 * <p>Vì thế bảng KHÔNG cần cột {@code published_at} hay cột khóa (locking): WAL mới là
 * nguồn sự thật cho việc phát event, còn bảng chỉ là điểm "bàn giao trong transaction"
 * (và sẽ được dọn bớt sau bởi OutboxPurgeJob).
 *
 * <p>Tên các cột được đặt khớp với field mapping của Debezium Outbox Event Router.
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
