package com.manhpham.inventory.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Trạng thái bán bền của MỘT ghế (nguồn sự thật). {@code seat_id} = Catalog.seat_map.id.
 * Giữ chỗ tạm lúc flash sale ở Redis (inv:seat:{id} SET NX); chốt SOLD ghi xuống đây.
 */
@Entity
@Table(name = "seat_inventory")
@Getter
public class SeatInventory {

    @Id
    @Column(name = "seat_id", nullable = false, updatable = false)
    private UUID seatId;

    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SeatInventory() {
        // for JPA
    }

    public static SeatInventory create(UUID seatId, UUID ticketTypeId, UUID eventId) {
        SeatInventory s = new SeatInventory();
        s.seatId = seatId;
        s.ticketTypeId = ticketTypeId;
        s.eventId = eventId;
        s.status = SeatStatus.AVAILABLE;
        s.updatedAt = Instant.now();
        return s;
    }

    /** Re-seed (chỉ khi DRAFT): trả ghế về AVAILABLE, bỏ tham chiếu đơn. */
    public void resetAvailable() {
        this.status = SeatStatus.AVAILABLE;
        this.orderId = null;
    }

    /** Chốt bán ghế cho một đơn (idempotent: gọi lại với cùng order là no-op an toàn). */
    public void markSold(UUID orderId) {
        this.status = SeatStatus.SOLD;
        this.orderId = orderId;
    }

    public boolean isSold() {
        return status == SeatStatus.SOLD;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
