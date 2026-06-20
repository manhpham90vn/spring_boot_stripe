package com.manhpham.payment.services;

import com.manhpham.payment.dto.ChargeRequest;
import com.manhpham.payment.dto.ChargeResponse;

public interface PaymentService {

    /** Thu tiền cho đơn; idempotent theo orderId (gọi lại trả kết quả cũ, không thu lại). */
    ChargeResponse charge(ChargeRequest request);

    /** Tra trạng thái thanh toán của một đơn (cho reconciliation phía Order). 404 nếu chưa có. */
    ChargeResponse getByOrder(java.util.UUID orderId);
}
