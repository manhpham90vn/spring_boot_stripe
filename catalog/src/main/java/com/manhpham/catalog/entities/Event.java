package com.manhpham.catalog.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Một sự kiện (concert) gắn với một địa điểm. Chỉ tham chiếu Venue qua
 * {@code venueId} (không map quan hệ JPA) — giữ aggregate gọn, tránh lazy/N+1;
 * Catalog ghép Venue ở tầng service khi cần.
 */
@Entity
@Table(name = "events")
@Getter
public class Event {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EventStatus status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "sales_start_at")
    private Instant salesStartAt;

    @Column(name = "sales_end_at")
    private Instant salesEndAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {
        // for JPA
    }

    public static Event create(UUID venueId, String title, String description,
            Instant startsAt, Instant salesStartAt, Instant salesEndAt) {
        Event e = new Event();
        e.venueId = venueId;
        e.title = title;
        e.description = description;
        e.startsAt = startsAt;
        e.salesStartAt = salesStartAt;
        e.salesEndAt = salesEndAt;
        e.status = EventStatus.DRAFT;
        return e;
    }

    /** Đổi trạng thái hiển thị/bán (admin). */
    public void changeStatus(EventStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * Cập nhật thông tin sự kiện (admin). Chỉ gọi khi còn DRAFT — guard ở tầng
     * service; không đổi {@code status} qua đây (dùng {@link #changeStatus}).
     */
    public void update(UUID venueId, String title, String description,
            Instant startsAt, Instant salesStartAt, Instant salesEndAt) {
        this.venueId = venueId;
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.salesStartAt = salesStartAt;
        this.salesEndAt = salesEndAt;
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
