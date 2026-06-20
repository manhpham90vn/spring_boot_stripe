package com.manhpham.payment.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * Đánh dấu một Stripe event ĐÃ xử lý → chống xử lý trùng side-effect khi webhook tới nhiều
 * lần (payment_issue.md 2.8). Khoá chính = Stripe Event.id.
 */
@Entity
@Table(name = "processed_events")
@Getter
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 120, updatable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // for JPA
    }

    public static ProcessedEvent create(String eventId, String eventType) {
        ProcessedEvent e = new ProcessedEvent();
        e.eventId = eventId;
        e.eventType = eventType;
        e.processedAt = Instant.now();
        return e;
    }
}
