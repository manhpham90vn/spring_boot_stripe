package com.manhpham.payment.utils.exception;

import java.util.UUID;

/**
 * PaymentIntent không hủy được vì tiền đã (hoặc đang) được thu — PI ở succeeded/processing,
 * hoặc bản ghi đã SUCCEEDED/REFUNDED. Trả 409 để Order GIỮ đơn ở AWAITING_PAYMENT và chờ
 * webhook/reconciliation chốt kết quả thật (saga-purchase-flow.md §4).
 */
public class PaymentNotCancellableException extends RuntimeException {

    public PaymentNotCancellableException(UUID orderId, String status) {
        super("Không thể hủy payment của order=" + orderId + " ở trạng thái " + status);
    }
}
