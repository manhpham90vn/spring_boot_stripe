package com.manhpham.catalog.entities;

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
 * Loại vé của một sự kiện ("vé như sản phẩm" — KHÁC "vé đã phát" ở Ticket service).
 * Giá lưu theo đơn vị nhỏ nhất (minor units) + mã tiền tệ ISO-4217 để tránh sai số
 * dấu phẩy động và bug ×100 với tiền tệ zero-decimal (vd JPY).
 *
 * <p>KHÔNG chứa số lượng tồn — tồn kho thuộc Inventory service.
 */
@Entity
@Table(name = "ticket_types")
@Getter
public class TicketType {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "max_per_order", nullable = false)
    private int maxPerOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketType() {
        // for JPA
    }

    public static TicketType create(UUID eventId, String name, String description,
            long priceMinor, String currency, int maxPerOrder) {
        TicketType t = new TicketType();
        t.eventId = eventId;
        t.name = name;
        t.description = description;
        t.priceMinor = priceMinor;
        t.currency = currency;
        t.maxPerOrder = maxPerOrder;
        return t;
    }

    /** Cập nhật loại vé (admin). Chỉ gọi khi sự kiện còn DRAFT — guard ở service. */
    public void update(String name, String description, long priceMinor,
            String currency, int maxPerOrder) {
        this.name = name;
        this.description = description;
        this.priceMinor = priceMinor;
        this.currency = currency;
        this.maxPerOrder = maxPerOrder;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
