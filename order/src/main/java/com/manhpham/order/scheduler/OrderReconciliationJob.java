package com.manhpham.order.scheduler;

import com.manhpham.order.client.PaymentClient;
import com.manhpham.order.entities.Order;
import com.manhpham.order.entities.OrderStatus;
import com.manhpham.order.event.PaymentSettledEvent;
import com.manhpham.order.repositories.jpa.OrderRepository;
import com.manhpham.order.services.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Lưới an toàn cho saga (payment_issue.md 3.3/3.4): nếu event {@code payment.events} bị MẤT,
 * đơn sẽ kẹt {@code AWAITING_PAYMENT}. Job này định kỳ HỎI LẠI Payment (nguồn sự thật) cho các
 * đơn kẹt quá lâu rồi tự tiếp tục/bù trừ saga qua {@code onPaymentSettled}.
 */
@Component
@Slf4j
public class OrderReconciliationJob {

    private final OrderRepository orders;
    private final PaymentClient payment;
    private final OrderService orderService;
    private final Duration stuckAfter;

    public OrderReconciliationJob(OrderRepository orders, PaymentClient payment, OrderService orderService,
                                  @Value("${order.reconcile.stuck-after:PT2M}") Duration stuckAfter) {
        this.orders = orders;
        this.payment = payment;
        this.orderService = orderService;
        this.stuckAfter = stuckAfter;
    }

    @Scheduled(fixedDelayString = "${order.reconcile.interval:PT2M}")
    public void reconcile() {
        Instant cutoff = Instant.now().minus(stuckAfter);
        for (Order o : orders.findByStatusAndUpdatedAtBefore(OrderStatus.AWAITING_PAYMENT, cutoff)) {
            try {
                PaymentClient.ChargeResult cr = payment.byOrder(o.getId());
                if ("SUCCEEDED".equals(cr.status()) || "FAILED".equals(cr.status())) {
                    log.warn("Reconcile đơn kẹt {} → payment {} → tiếp tục saga", o.getId(), cr.status());
                    // Tái tạo sự kiện từ trạng thái Payment để đẩy saga đi tiếp (idempotent).
                    orderService.onPaymentSettled(new PaymentSettledEvent(
                            o.getId(), cr.paymentId(), cr.status(), cr.reference(), Instant.now()));
                }
                // PENDING/PROCESSING (async Konbini chưa trả) → để vòng sau.
            } catch (RuntimeException e) {
                // 404 (payment chưa tồn tại) hoặc lỗi tạm thời → bỏ qua, thử lại vòng sau.
                log.debug("Reconcile đơn {} chưa xử lý được: {}", o.getId(), e.getMessage());
            }
        }
    }
}
