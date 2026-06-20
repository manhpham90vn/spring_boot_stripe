package com.manhpham.payment.gateway;

import java.util.UUID;

/**
 * Trừu tượng cổng thanh toán → tách nghiệp vụ khỏi nhà cung cấp cụ thể. Có hai hiện thực:
 * {@link MockPaymentGateway} (dev, tự succeeded) và {@link StripePaymentGateway} (thật).
 * Chọn qua property {@code payment.gateway=mock|stripe} (mặc định mock).
 */
public interface PaymentGateway {

    /**
     * Thu tiền cho một đơn. {@code idempotencyKey} đảm bảo timeout/retry không thu hai lần
     * (payment_issue.md 2.2/2.18). amount theo minor units.
     */
    ChargeOutcome charge(UUID orderId, long amountMinor, String currency, String idempotencyKey);

    /** Kết quả thô từ cổng: tham chiếu (PI id) + trạng thái. */
    record ChargeOutcome(String reference, Status status) {
        public enum Status { SUCCEEDED, FAILED }
    }
}
