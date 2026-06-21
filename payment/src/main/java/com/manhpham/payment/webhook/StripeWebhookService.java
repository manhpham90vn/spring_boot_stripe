package com.manhpham.payment.webhook;

import com.manhpham.payment.entities.Payment;
import com.manhpham.payment.entities.PaymentStatus;
import com.manhpham.payment.entities.ProcessedEvent;
import com.manhpham.payment.event.PaymentSettledEvent;
import com.manhpham.payment.event.PaymentSettledOutboxEvent;
import com.manhpham.payment.handle.OutboxEventSender;
import com.manhpham.payment.repositories.jpa.PaymentRepository;
import com.manhpham.payment.repositories.jpa.ProcessedEventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Xử lý webhook Stripe — NGUỒN SỰ THẬT cho "đã thanh toán hay chưa" (payment_issue.md 2.4).
 *
 * <p>Bốn lớp an toàn (payment_issue.md 4.2/2.8/2.9):
 * <ol>
 *   <li><b>Verify chữ ký + timestamp tolerance</b>: {@link Webhook#constructEvent} dùng
 *       {@code stripe.webhook-secret} — chống giả mạo & replay.</li>
 *   <li><b>Idempotent</b>: bỏ qua nếu {@code event.id} đã xử lý (bảng processed_events).</li>
 *   <li><b>Đối chiếu amount/currency</b> của PI với đơn — chống thao túng PI trả ít hơn.</li>
 *   <li>Toàn bộ trong MỘT {@code @Transactional}: cập nhật payment + ghi processed_events
 *       commit nguyên tử.</li>
 * </ol>
 *
 * <p>Cập nhật bản ghi PAYMENT + phát {@code PaymentSettled} qua OUTBOX (cùng transaction) →
 * Kafka {@code payment.events} để <b>Order/saga tiếp tục</b> (commit Inventory + Order PAID +
 * phát vé) — xem flows/saga-purchase-flow.md §2.1.
 */
@Service
@Slf4j
public class StripeWebhookService {

    private final PaymentRepository payments;
    private final ProcessedEventRepository processedEvents;
    private final OutboxEventSender outbox;
    private final String webhookSecret;

    public StripeWebhookService(PaymentRepository payments,
                                ProcessedEventRepository processedEvents,
                                OutboxEventSender outbox,
                                @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.payments = payments;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public void handle(String payload, String signature) throws SignatureVerificationException {
        // (1) Verify chữ ký Stripe (đã bao gồm kiểm timestamp tolerance ~5 phút).
        Event event = Webhook.constructEvent(payload, signature, webhookSecret);

        // (2) Idempotent: event đã xử lý rồi thì bỏ qua (webhook at-least-once, có thể trùng).
        if (processedEvents.existsById(event.getId())) {
            log.info("Webhook {} đã xử lý trước đó — bỏ qua", event.getId());
            return;
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> applyStatus(event, PaymentStatus.SUCCEEDED);
            case "payment_intent.payment_failed" -> applyStatus(event, PaymentStatus.FAILED);
            case "payment_intent.canceled" -> applyStatus(event, PaymentStatus.CANCELED);
            // Async Konbini/Furikomi (checkout.session.async_payment_*) cần thêm bước tạo Checkout
            // Session — bổ sung ở increment sau (xem impl/01-payment-real-stripe.md §0).
            default -> log.debug("Webhook bỏ qua loại {}", event.getType());
        }

        // Đánh dấu đã xử lý (cùng transaction với cập nhật payment).
        processedEvents.save(ProcessedEvent.create(event.getId(), event.getType()));
    }

    private void applyStatus(Event event, PaymentStatus status) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(obj instanceof PaymentIntent pi)) {
            log.warn("Webhook {} không deserialize được PaymentIntent", event.getId());
            return;
        }
        String orderId = pi.getMetadata() == null ? null : pi.getMetadata().get("order_id");
        if (orderId == null) {
            log.warn("PaymentIntent {} thiếu metadata order_id — không map được đơn", pi.getId());
            return;
        }

        Payment payment = payments.findByOrderId(UUID.fromString(orderId)).orElse(null);
        if (payment == null) {
            log.warn("Không thấy payment cho order={} (PI {})", orderId, pi.getId());
            return;
        }

        // (3) Đối chiếu số tiền + tiền tệ với đơn — chống thao túng PI (payment_issue.md 2.9).
        Long piAmount = pi.getAmount();
        String piCurrency = pi.getCurrency();
        if (piAmount == null || piAmount != payment.getAmountMinor()
                || piCurrency == null || !piCurrency.equalsIgnoreCase(payment.getCurrency())) {
            log.error("Webhook LỆCH số tiền cho order={}: PI={} {} vs đơn={} {} — KHÔNG áp dụng",
                    orderId, piAmount, piCurrency, payment.getAmountMinor(), payment.getCurrency());
            return;
        }

        payment.settle(status, pi.getId()); // dirty checking persist
        // Phát PaymentSettled (cùng transaction) → Order/saga tiếp tục cho thanh toán bất đồng bộ.
        outbox.fire(PaymentSettledOutboxEvent.of(PaymentSettledEvent.of(
                payment.getOrderId(), payment.getId(), status.name(), pi.getId())));
        log.info("Webhook cập nhật payment order={} -> {} (PI {})", orderId, status, pi.getId());
    }
}
