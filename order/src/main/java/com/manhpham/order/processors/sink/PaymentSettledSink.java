package com.manhpham.order.processors.sink;

import com.manhpham.common.core.event.PaymentSettledEvent;
import com.manhpham.order.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Consumer Kafka cho topic {@code payment.events}: nhận {@code PaymentSettled} từ Payment để
 * TIẾP TỤC saga (chốt SOLD + PAID, hoặc nhả chỗ + PAYMENT_FAILED). Bean name = "paymentSettledSink"
 * khớp {@code spring.cloud.function.definition}. Idempotent ở tầng service.
 */
@Component
@RequiredArgsConstructor
public class PaymentSettledSink implements Consumer<PaymentSettledEvent> {

    private final OrderService orderService;

    @Override
    public void accept(PaymentSettledEvent event) {
        orderService.onPaymentSettled(event);
    }
}
