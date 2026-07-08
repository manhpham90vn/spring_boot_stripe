package com.manhpham.payment.services.impl;

import com.manhpham.payment.dto.ChargeResponse;
import com.manhpham.payment.dto.CreateIntentRequest;
import com.manhpham.payment.dto.IntentResponse;
import com.manhpham.payment.entities.Payment;
import com.manhpham.payment.entities.PaymentStatus;
import com.manhpham.payment.gateway.PaymentGateway;
import com.manhpham.payment.repositories.jpa.PaymentRepository;
import com.manhpham.payment.services.PaymentService;
import com.manhpham.payment.utils.exception.PaymentNotCancellableException;
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

    @Override
    @Transactional
    public ChargeResponse refund(UUID orderId) {
        Payment payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        // Idempotent: đã hoàn rồi → trả lại, KHÔNG gọi Stripe lần nữa (saga retry an toàn).
        if (payment.isRefunded()) {
            log.info("refund idempotent hit order={} (đã REFUNDED)", orderId);
            return ChargeResponse.from(payment);
        }
        // Chỉ hoàn được khi đã THẬT SỰ thu tiền (webhook succeeded) — nếu không thì không có gì để hoàn.
        if (!payment.isSucceeded()) {
            throw new IllegalStateException(
                    "Không thể refund order=" + orderId + " ở trạng thái " + payment.getStatus());
        }

        // Idempotency key ổn định theo đơn → Stripe KHÔNG hoàn hai lần dù saga gọi lại.
        String refundRef = gateway.refund(payment.getStripePiId(), "refund:order:" + orderId);
        payment.refund(refundRef);
        log.info("Payment order={} -> REFUNDED ref={}", orderId, refundRef);
        return ChargeResponse.from(payment);
    }

    @Override
    @Transactional
    public ChargeResponse cancel(UUID orderId) {
        Payment payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        // Idempotent: đã hủy rồi → trả lại, KHÔNG gọi Stripe lần nữa (saga retry an toàn).
        if (payment.getStatus() == PaymentStatus.CANCELED) {
            log.info("cancel idempotent hit order={} (đã CANCELED)", orderId);
            return ChargeResponse.from(payment);
        }
        // Tiền đã thu/đã hoàn → KHÔNG hủy được; Order phải giữ đơn chờ luồng succeeded/refund.
        if (payment.isSucceeded() || payment.isRefunded()) {
            throw new PaymentNotCancellableException(orderId, payment.getStatus().name());
        }
        // Chưa từng tạo được PI ở Stripe → chỉ đóng bản ghi local.
        if (payment.getStripePiId() == null) {
            payment.settle(PaymentStatus.CANCELED, null);
            log.info("Payment order={} -> CANCELED (chưa có PI)", orderId);
            return ChargeResponse.from(payment);
        }

        String stripeStatus = gateway.cancelIntent(payment.getStripePiId());
        if (!"canceled".equals(stripeStatus)) {
            // PI đã kịp succeeded/processing (khách xác nhận đúng lúc hủy) → webhook sẽ chốt.
            throw new PaymentNotCancellableException(orderId, stripeStatus);
        }
        payment.settle(PaymentStatus.CANCELED, payment.getStripePiId());
        log.info("Payment order={} -> CANCELED (PI {})", orderId, payment.getStripePiId());
        return ChargeResponse.from(payment);
    }
}
