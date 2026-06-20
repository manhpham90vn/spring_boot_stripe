package com.manhpham.payment.entities;

/**
 * Trạng thái thanh toán (rút gọn cho walking skeleton). Thực tế Stripe có nhiều trạng thái
 * trung gian (processing/requires_action...) — xem payment-stripe-flow.md §6.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
