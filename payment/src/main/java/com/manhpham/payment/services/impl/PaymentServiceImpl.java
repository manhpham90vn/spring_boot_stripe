package com.manhpham.payment.services.impl;

import com.manhpham.payment.dto.ChargeRequest;
import com.manhpham.payment.dto.ChargeResponse;
import com.manhpham.payment.entities.Payment;
import com.manhpham.payment.entities.PaymentStatus;
import com.manhpham.payment.event.PaymentSettledEvent;
import com.manhpham.payment.event.PaymentSettledOutboxEvent;
import com.manhpham.payment.gateway.PaymentGateway;
import com.manhpham.payment.handle.OutboxEventSender;
import com.manhpham.payment.repositories.jpa.PaymentRepository;
import com.manhpham.payment.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OutboxEventSender outbox;

    @Override
    @Transactional
    public ChargeResponse charge(ChargeRequest request) {
        // Idempotency lớp 1: đã có payment cho đơn này → trả kết quả cũ, KHÔNG thu lại.
        Payment existing = payments.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            log.info("Charge idempotent hit order={} status={}", request.orderId(), existing.getStatus());
            return ChargeResponse.from(existing);
        }

        Payment payment;
        try {
            // saveAndFlush để unique(order_id) bật lỗi ngay nếu hai request cùng đơn chạy song song.
            payment = payments.saveAndFlush(
                    Payment.create(request.orderId(), request.amountMinor(), request.currency()));
        } catch (DataIntegrityViolationException race) {
            return ChargeResponse.from(payments.findByOrderId(request.orderId()).orElseThrow());
        }

        // Idempotency key ổn định theo đơn (payment_issue.md 2.17) → cổng không thu hai lần.
        String idemKey = "order:" + request.orderId() + ":attempt:1";
        PaymentGateway.ChargeOutcome outcome =
                gateway.charge(request.orderId(), request.amountMinor(), request.currency(), idemKey);

        PaymentStatus status = outcome.status() == PaymentGateway.ChargeOutcome.Status.SUCCEEDED
                ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
        payment.settle(status, outcome.reference()); // dirty checking persist

        // Phát PaymentSettled qua OUTBOX (cùng transaction) → Order/saga tiếp tục qua payment.events.
        // (Với async Konbini/Furikomi, gateway sẽ trả PROCESSING và sự kiện này phát SAU, từ webhook.)
        outbox.fire(PaymentSettledOutboxEvent.of(PaymentSettledEvent.of(
                payment.getOrderId(), payment.getId(), status.name(), outcome.reference())));
        log.info("Charge order={} amount={} {} -> {}", request.orderId(), request.amountMinor(),
                request.currency(), status);
        return ChargeResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeResponse getByOrder(java.util.UUID orderId) {
        return payments.findByOrderId(orderId)
                .map(ChargeResponse::from)
                .orElseThrow(() -> new com.manhpham.payment.utils.exception.PaymentNotFoundException(orderId));
    }
}
