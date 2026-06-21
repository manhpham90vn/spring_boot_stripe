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
}
