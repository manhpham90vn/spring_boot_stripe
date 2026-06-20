package com.manhpham.inventory.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Nguồn sự thật BỀN của tồn kho một loại vé (GA). Redis giữ counter "còn lại" để tranh chấp
 * nhanh; entity này chốt tổng phát hành + số đã bán (SOLD) xuống PostgreSQL. Khi khởi động
 * lại / nghi lệch, dựng lại counter Redis = {@code total_qty - sold_qty} (xem inventory-no-oversell.md §6).
 */
@Entity
@Table(name = "ticket_stock")
@Getter
public class TicketStock {

    @Id
    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "sold_qty", nullable = false)
    private int soldQty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketStock() {
        // for JPA
    }

    public static TicketStock create(UUID ticketTypeId, UUID eventId, int totalQty) {
        TicketStock s = new TicketStock();
        s.ticketTypeId = ticketTypeId;
        s.eventId = eventId;
        s.totalQty = totalQty;
        s.soldQty = 0;
        return s;
    }

    /** Đặt lại tổng tồn (seed/chỉnh). Không cho nhỏ hơn số đã bán. */
    public void resetTotal(int totalQty) {
        if (totalQty < soldQty) {
            throw new IllegalArgumentException("total_qty không được nhỏ hơn sold_qty");
        }
        this.totalQty = totalQty;
    }

    /** Chốt SOLD khi commit hold (số đã bán tăng lên). Đây là bước ghi bền nguồn sự thật. */
    public void commitSold(int qty) {
        this.soldQty += qty;
    }

    /** Trả lại SOLD khi hoàn/huỷ sau khi đã commit (restock). */
    public void restock(int qty) {
        this.soldQty = Math.max(0, this.soldQty - qty);
    }

    public int available() {
        return totalQty - soldQty;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
