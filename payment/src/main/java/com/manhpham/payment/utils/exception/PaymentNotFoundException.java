package com.manhpham.payment.utils.exception;

import java.util.UUID;

/** Chưa có bản ghi thanh toán cho đơn này. → HTTP 404. */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID orderId) {
        super("Payment not found for order: " + orderId);
    }
}
