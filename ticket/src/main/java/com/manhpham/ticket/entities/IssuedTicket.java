package com.manhpham.ticket.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Vé THẬT đã phát ra sau khi đơn hoàn tất. {@code qrToken} = "{id}.{HMAC}" ký số (xem
 * QrSigner) → khi quét tại cổng, verify chữ ký rồi đánh dấu USED. Một-vé-một-lượt-vào.
 */
@Entity
@Table(name = "issued_tickets")
@Getter
public class IssuedTicket {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(name = "qr_token", nullable = false, columnDefinition = "text")
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected IssuedTicket() {
        // for JPA
    }

    /** Tạo vé mới (id sinh sẵn để QrSigner ký lên). Trạng thái khởi đầu VALID. */
    public static IssuedTicket create(UUID orderId, UUID userId, UUID eventId, UUID ticketTypeId) {
        IssuedTicket t = new IssuedTicket();
        t.id = UUID.randomUUID();
        t.orderId = orderId;
        t.userId = userId;
        t.eventId = eventId;
        t.ticketTypeId = ticketTypeId;
        t.status = TicketStatus.VALID;
        t.issuedAt = Instant.now();
        return t;
    }

    public void assignQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    /** Đánh dấu đã vào cổng. Idempotent-an toàn: chỉ chuyển khi đang VALID. */
    public void markUsed() {
        this.status = TicketStatus.USED;
        this.usedAt = Instant.now();
    }

    public boolean isValid() {
        return status == TicketStatus.VALID;
    }
}
