package com.manhpham.payment.entities;

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
 * Bản ghi thanh toán của một đơn. {@code order_id} UNIQUE → mỗi đơn tối đa một payment
 * (lớp idempotency ở DB). amount theo minor units + currency.
 */
@Entity
@Table(name = "payments")
@Getter
public class Payment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "payment_ref")
    private String paymentRef;

    @Column(name = "stripe_pi_id")
    private String stripePiId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // for JPA
    }

    public static Payment create(UUID orderId, long amountMinor, String currency) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.orderId = orderId;
        p.amountMinor = amountMinor;
        p.currency = currency;
        p.status = PaymentStatus.PENDING;
        return p;
    }

    /** Đã tạo PaymentIntent ở Stripe → PROCESSING, lưu tham chiếu PI. Chờ webhook chốt. */
    public void startIntent(String paymentIntentId) {
        this.status = PaymentStatus.PROCESSING;
        this.stripePiId = paymentIntentId;
        this.paymentRef = paymentIntentId;
    }

    /** Chốt kết quả cuối từ webhook Stripe (succeeded/failed/canceled). */
    public void settle(PaymentStatus status, String paymentRef) {
        this.status = status;
        this.paymentRef = paymentRef;
    }

    /** Đã hoàn tiền (bù trừ saga khi đã thu tiền nhưng hết vé — saga-purchase-flow.md §4). */
    public void refund(String refundRef) {
        this.status = PaymentStatus.REFUNDED;
        this.paymentRef = refundRef;
    }

    public boolean isSucceeded() {
        return status == PaymentStatus.SUCCEEDED;
    }

    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
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
