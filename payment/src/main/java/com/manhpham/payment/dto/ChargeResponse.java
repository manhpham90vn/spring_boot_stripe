package com.manhpham.payment.dto;

import com.manhpham.payment.entities.Payment;
import com.manhpham.payment.entities.PaymentStatus;

import java.util.UUID;

/** Trạng thái thanh toán của một đơn (cho Order reconciliation). */
public record ChargeResponse(UUID paymentId, UUID orderId, PaymentStatus status, String reference,
                             String stripePiId) {

    public static ChargeResponse from(Payment p) {
        return new ChargeResponse(p.getId(), p.getOrderId(), p.getStatus(), p.getPaymentRef(), p.getStripePiId());
    }
}
