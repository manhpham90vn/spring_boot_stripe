package com.manhpham.payment.dto;

import com.manhpham.payment.entities.Payment;
import com.manhpham.payment.entities.PaymentStatus;

import java.util.UUID;

/** Kết quả thu tiền trả về Order. */
public record ChargeResponse(UUID paymentId, UUID orderId, PaymentStatus status, String reference) {

    public static ChargeResponse from(Payment p) {
        return new ChargeResponse(p.getId(), p.getOrderId(), p.getStatus(), p.getPaymentRef());
    }
}
