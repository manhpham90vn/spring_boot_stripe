package com.manhpham.order.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Đơn hàng + TRẠNG THÁI SAGA. Ngoài thông tin đơn, entity giữ {@code holdId}/{@code paymentId}
 * để biết saga đang ở bước nào và phải bù trừ gì khi lỗi (xem saga-purchase-flow.md §3).
 * Bảng tên {@code orders} (tránh từ khoá SQL "order").
 */
@Entity
@Table(name = "orders")
@Getter
public class Order {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(length = 320, updatable = false)
    private String email;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    // SEATED: danh sách ghế đã chọn (= Catalog.seat_map.id). NULL với GA.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "seat_ids", columnDefinition = "uuid[]", updatable = false)
    private UUID[] seatIds;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "hold_id")
    private UUID holdId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "stripe_pi_id")
    private String stripePiId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
        // for JPA
    }

    public static Order create(UUID userId, String email, UUID eventId, UUID ticketTypeId, int quantity,
                               UUID[] seatIds, long amountMinor, String currency) {
        Order o = new Order();
        o.id = UUID.randomUUID();
        o.userId = userId;
        o.email = email;
        o.eventId = eventId;
        o.ticketTypeId = ticketTypeId;
        o.quantity = quantity;
        o.seatIds = seatIds;
        o.amountMinor = amountMinor;
        o.currency = currency;
        o.status = OrderStatus.PENDING;
        return o;
    }

    /** Đã giữ chỗ + đã tạo PaymentIntent → chờ webhook (event PaymentSettled). Lưu tham chiếu để resume/bù trừ. */
    public void awaitPayment(UUID holdId, UUID paymentId, String stripePiId) {
        this.holdId = holdId;
        this.paymentId = paymentId;
        this.stripePiId = stripePiId;
        this.status = OrderStatus.AWAITING_PAYMENT;
    }

    public boolean isAwaitingPayment() {
        return status == OrderStatus.AWAITING_PAYMENT;
    }

    public void markPaid(UUID paymentId) {
        this.paymentId = paymentId;
        this.status = OrderStatus.PAID;
    }

    public void reject(String reason) {
        this.status = OrderStatus.REJECTED;
        this.failureReason = reason;
    }

    public void failPayment(String reason) {
        this.status = OrderStatus.PAYMENT_FAILED;
        this.failureReason = reason;
    }

    /** Bù trừ saga §4: đã thu tiền nhưng hết vé lúc chốt SOLD → đã refund + nhả chỗ. */
    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.failureReason = reason;
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
