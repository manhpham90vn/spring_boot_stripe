package com.manhpham.payment.services;

import com.manhpham.payment.dto.ChargeResponse;
import com.manhpham.payment.dto.CreateIntentRequest;
import com.manhpham.payment.dto.IntentResponse;

public interface PaymentService {

    /**
     * Tạo PaymentIntent cho đơn (chưa thu tiền — chờ client xác nhận + webhook). Idempotent theo
     * orderId (gọi lại trả intent cũ, không tạo hai). Trả clientSecret cho FE.
     */
    IntentResponse createIntent(CreateIntentRequest request);

    /** Tra trạng thái thanh toán của một đơn (cho reconciliation phía Order). 404 nếu chưa có. */
    ChargeResponse getByOrder(java.util.UUID orderId);

    /**
     * Hoàn tiền cho đơn — bù trừ saga khi đã thu tiền nhưng không chốt được SOLD
     * (saga-purchase-flow.md §4). Idempotent: gọi lại khi đã REFUNDED trả lại trạng thái, KHÔNG
     * hoàn hai lần. 404 nếu chưa có payment.
     */
    ChargeResponse refund(java.util.UUID orderId);

    /**
     * HỦY PaymentIntent của đơn — bù trừ saga khi thanh toán thất bại và Order bỏ cuộc; Order
     * phải gọi TRƯỚC khi nhả chỗ (saga-purchase-flow.md §4). Idempotent: đã CANCELED thì trả
     * lại. Ném {@link com.manhpham.payment.utils.exception.PaymentNotCancellableException}
     * (409) nếu tiền đã/đang được thu — Order phải giữ đơn chờ webhook chốt. 404 nếu chưa có.
     */
    ChargeResponse cancel(java.util.UUID orderId);
}
