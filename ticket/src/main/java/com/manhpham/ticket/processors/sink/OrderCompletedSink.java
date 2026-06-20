package com.manhpham.ticket.processors.sink;

import com.manhpham.ticket.event.OrderCompletedEvent;
import com.manhpham.ticket.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Consumer Kafka cho topic {@code order.events}: mỗi {@code OrderCompleted} → phát vé.
 * Bean name = "orderCompletedSink" (tên class) khớp {@code spring.cloud.function.definition};
 * binding {@code orderCompletedSink-in-0} trỏ tới {@code order.events} (application.properties).
 * Idempotent ở tầng service (TicketServiceImpl) vì Kafka giao at-least-once.
 */
@Component
@RequiredArgsConstructor
public class OrderCompletedSink implements Consumer<OrderCompletedEvent> {

    private final TicketService ticketService;

    @Override
    public void accept(OrderCompletedEvent event) {
        ticketService.issueForOrder(event);
    }
}
