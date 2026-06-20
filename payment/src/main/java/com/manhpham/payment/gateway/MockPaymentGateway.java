package com.manhpham.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cổng thanh toán GIẢ cho dev: luôn trả {@code SUCCEEDED} ngay, KHÔNG gọi Stripe — để luồng
 * mua chạy được end-to-end trên máy mà không cần khoá Stripe thật. Bật mặc định
 * ({@code payment.gateway=mock} hoặc bỏ trống).
 */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public ChargeOutcome charge(UUID orderId, long amountMinor, String currency, String idempotencyKey) {
        String ref = "mock_pi_" + UUID.randomUUID();
        log.info("[MOCK] charge order={} amount={} {} -> SUCCEEDED ref={}", orderId, amountMinor, currency, ref);
        return new ChargeOutcome(ref, ChargeOutcome.Status.SUCCEEDED);
    }
}
