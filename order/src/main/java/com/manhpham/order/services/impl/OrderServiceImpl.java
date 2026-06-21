package com.manhpham.order.services.impl;

import com.manhpham.order.client.CatalogClient;
import com.manhpham.order.client.InventoryClient;
import com.manhpham.order.client.PaymentClient;
import com.manhpham.order.dto.OrderResponse;
import com.manhpham.order.dto.PlaceOrderRequest;
import com.manhpham.order.entities.Order;
import com.manhpham.order.event.OrderCompletedEvent;
import com.manhpham.order.event.OrderCompletedOutboxEvent;
import com.manhpham.order.event.PaymentSettledEvent;
import com.manhpham.order.handle.OutboxEventSender;
import com.manhpham.order.repositories.jpa.OrderRepository;
import com.manhpham.order.services.OrderService;
import com.manhpham.order.utils.exception.OrderNotFoundException;
import com.manhpham.order.utils.exception.TicketTypeUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

/**
 * SAGA ORCHESTRATOR event-driven (xem saga-purchase-flow.md §2.1, §3):
 *
 * <pre>
 *  place():  tạo đơn → giữ chỗ → TẠO PaymentIntent → AWAITING_PAYMENT (commit) → trả clientSecret.
 *            (saga DỪNG; client xác nhận thẻ qua Payment Element, kết quả về từ webhook)
 *  onPaymentSettled():  SUCCEEDED → chốt SOLD + PAID + phát OrderCompleted
 *                       FAILED    → nhả chỗ + PAYMENT_FAILED   (bù trừ)
 * </pre>
 *
 * <p>place() KHÔNG bọc một {@code @Transactional} lớn: mỗi bước commit riêng (step-wise saga)
 * để không giữ kết nối DB suốt lời gọi remote. Đơn commit ở AWAITING_PAYMENT sau khi tạo
 * PaymentIntent; webhook (→ {@code payment.events}) chỉ tới SAU khi client xác nhận nên không race.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orders;
    private final OutboxEventSender outbox;
    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final PaymentClient payment;

    @Override
    public OrderResponse place(UUID userId, String email, PlaceOrderRequest req) {
        // (1) Giá tính ở SERVER từ Catalog — không tin client (payment_issue.md 4.1).
        CatalogClient.TicketTypeInfo tt;
        try {
            tt = catalog.getTicketType(req.eventId(), req.ticketTypeId());
        } catch (HttpClientErrorException.NotFound e) {
            throw new TicketTypeUnavailableException(req.ticketTypeId());
        }
        long amount = tt.priceMinor() * req.quantity();

        // (2) Tạo đơn PENDING (commit ngay — save() là một transaction riêng).
        Order order = orders.save(Order.create(
                userId, email, req.eventId(), req.ticketTypeId(), req.quantity(), amount, tt.currency()));

        // (3) GIỮ CHỖ. Hết vé (409) → REJECTED, không cần bù trừ.
        InventoryClient.HoldResult hold;
        try {
            hold = inventory.hold(req.ticketTypeId(), req.quantity(), order.getId());
        } catch (HttpClientErrorException.Conflict e) {
            order.reject("Hết vé / không đủ tồn");
            orders.save(order);
            log.info("Order {} REJECTED (sold out)", order.getId());
            return OrderResponse.from(order);
        }

        // (4) TẠO PaymentIntent. Lỗi gọi (Payment down) → bù trừ: nhả chỗ + PAYMENT_FAILED.
        PaymentClient.IntentResult intent;
        try {
            intent = payment.createIntent(order.getId(), amount, tt.currency());
        } catch (RuntimeException e) {
            inventory.release(hold.holdId());
            order.failPayment("Lỗi gọi Payment: " + e.getMessage());
            orders.save(order);
            log.warn("Order {} PAYMENT_FAILED (call error), released hold {}", order.getId(), hold.holdId());
            return OrderResponse.from(order);
        }

        // Tạo intent lỗi nghiệp vụ (4xx / hết retry) → không có webhook để chờ → bù trừ ngay.
        if (intent.failed()) {
            inventory.release(hold.holdId());
            order.failPayment("Khởi tạo thanh toán thất bại");
            orders.save(order);
            log.warn("Order {} PAYMENT_FAILED (intent failed), released hold {}", order.getId(), hold.holdId());
            return OrderResponse.from(order);
        }

        // (5) AWAITING_PAYMENT + COMMIT. Webhook chỉ tới SAU khi client xác nhận → không race với consumer.
        order.awaitPayment(hold.holdId(), intent.paymentId(), intent.stripePiId());
        orders.save(order);

        // Saga DỪNG ở AWAITING_PAYMENT — tiếp tục async qua onPaymentSettled (payment.events).
        // Trả clientSecret để FE xác nhận thẻ bằng Payment Element.
        log.info("Order {} AWAITING_PAYMENT (hold {}, PI {})", order.getId(), hold.holdId(), intent.stripePiId());
        return OrderResponse.withSecret(order, intent.clientSecret());
    }

    @Override
    @Transactional
    public void onPaymentSettled(PaymentSettledEvent event) {
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("PaymentSettled cho đơn không tồn tại {}", event.orderId());
            return;
        }
        // IDEMPOTENT: chỉ tiếp tục khi đơn đang chờ; đã PAID/FAILED rồi thì bỏ qua (at-least-once).
        if (!order.isAwaitingPayment()) {
            log.info("Order {} không ở AWAITING_PAYMENT ({}) — bỏ qua", order.getId(), order.getStatus());
            return;
        }

        if (event.succeeded()) {
            // Chốt SOLD ở Inventory (idempotent). Edge async (Konbini): nếu hold đã hết hạn ở
            // đây thì là ca "đã thu tiền nhưng hết vé" (payment_issue.md 1.4) → cần refund — TODO.
            inventory.commit(order.getHoldId());
            order.markPaid(event.paymentId());
            outbox.fire(OrderCompletedOutboxEvent.of(OrderCompletedEvent.of(
                    order.getId(), order.getUserId(), order.getEmail(), order.getEventId(),
                    order.getTicketTypeId(), order.getQuantity(), order.getAmountMinor(), order.getCurrency())));
            log.info("Order {} PAID (payment {})", order.getId(), event.paymentId());
        } else {
            inventory.release(order.getHoldId()); // bù trừ
            order.failPayment("Thanh toán thất bại");
            log.info("Order {} PAYMENT_FAILED, nhả chỗ {}", order.getId(), order.getHoldId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse get(UUID userId, UUID orderId) {
        Order order = orders.findById(orderId)
                .filter(o -> o.getUserId().equals(userId)) // IDOR: chỉ chủ đơn; khác → coi như 404
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.from(order);
    }
}
