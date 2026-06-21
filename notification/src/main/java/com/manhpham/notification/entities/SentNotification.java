package com.manhpham.notification.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản ghi một thông báo đã xử lý. {@code dedup_key} UNIQUE ⇒ mỗi event gửi đúng một lần
 * (chống Kafka at-least-once gửi trùng). Xem 07-notification.md.
 */
@Entity
@Table(name = "sent_notifications")
@Getter
public class SentNotification {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "dedup_key", nullable = false, unique = true, updatable = false)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private NotificationChannel channel;

    @Column(nullable = false, length = 320, updatable = false)
    private String recipient;

    @Column(nullable = false, length = 64, updatable = false)
    private String template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(length = 500)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SentNotification() {
        // for JPA
    }

    public static SentNotification create(String dedupKey, NotificationChannel channel,
                                          String recipient, String template) {
        SentNotification n = new SentNotification();
        n.id = UUID.randomUUID();
        n.dedupKey = dedupKey;
        n.channel = channel;
        n.recipient = recipient;
        n.template = template;
        n.status = NotificationStatus.FAILED; // mặc định; chuyển SENT khi gửi xong
        return n;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.error = null;
    }

    public void markFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.error = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    public boolean isSent() {
        return status == NotificationStatus.SENT;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
