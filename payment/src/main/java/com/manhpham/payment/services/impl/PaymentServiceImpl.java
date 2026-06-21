package com.manhpham.payment.services.impl;

import com.manhpham.payment.dto.ChargeResponse;
import com.manhpham.payment.dto.CreateIntentRequest;
import com.manhpham.payment.dto.IntentResponse;
import com.manhpham.payment.entities.Payment;
import com.manhpham.payment.entities.PaymentStatus;
import com.manhpham.payment.gateway.PaymentGateway;
import com.manhpham.payment.repositories.jpa.PaymentRepository;
import com.manhpham.payment.services.PaymentService;
import com.manhpham.payment.utils.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tạo PaymentIntent qua Stripe (KHÔNG thu tiền ngay). Kết quả thật chốt từ WEBHOOK
 * ({@link com.manhpham.payment.webhook.StripeWebhookService}) — service NÀY không phát
 * {@code PaymentSettled}. Xem impl/01-payment-real-stripe.md.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository payments;
    private final PaymentGateway gateway;

    @Override
    @Transactional
    public IntentResponse createIntent(CreateIntentRequest request) {
        // Idempotency lớp 1: đã có payment cho đơn → trả lại intent cũ (không tạo hai).
        Payment existing = payments.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            log.info("createIntent idempotent hit order={} status={}", request.orderId(), existing.getStatus());
            return IntentResponse.of(existing, retrieveSecret(existing));
        }

        Payment payment;
        try {
            // saveAndFlush để unique(order_id) bật lỗi ngay nếu hai request cùng đơn chạy song song.
            payment = payments.saveAndFlush(
                    Payment.create(request.orderId(), request.amountMinor(), request.currency()));
        } catch (DataIntegrityViolationException race) {
            Payment p = payments.findByOrderId(request.orderId()).orElseThrow();
            return IntentResponse.of(p, retrieveSecret(p));
        }

        // Idempotency key ổn định theo đơn → Stripe KHÔNG tạo hai PaymentIntent (payment_issue.md 2.17).
        String idemKey = "order:" + request.orderId();
        PaymentGateway.IntentResult outcome =
                gateway.createIntent(request.orderId(), request.amountMinor(), request.currency(), idemKey);

        if (outcome.status() == PaymentGateway.IntentResult.Status.FAILED) {
            // Lỗi tạo intent (4xx / hết retry) → FAILED. KHÔNG phát PaymentSettled (sẽ không có webhook);
            // Order tự bù trừ khi thấy status FAILED ở response.
            payment.settle(PaymentStatus.FAILED, null);
            log.warn("createIntent FAILED order={}", request.orderId());
            return IntentResponse.of(payment, null);
        }

        // PROCESSING: lưu PI, chờ webhook chốt. KHÔNG phát PaymentSettled ở đây.
        payment.startIntent(outcome.paymentIntentId());
        log.info("PaymentIntent {} order={} -> PROCESSING", outcome.paymentIntentId(), request.orderId());
        return IntentResponse.of(payment, outcome.clientSecret());
    }

    private String retrieveSecret(Payment p) {
        return p.getStripePiId() == null ? null : gateway.retrieveClientSecret(p.getStripePiId());
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeResponse getByOrder(UUID orderId) {
        return payments.findByOrderId(orderId)
                .map(ChargeResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }
}
