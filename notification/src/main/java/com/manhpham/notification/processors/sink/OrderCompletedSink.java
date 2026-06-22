package com.manhpham.notification.processors.sink;

import java.util.function.Consumer;

import com.manhpham.notification.entities.NotificationChannel;
import com.manhpham.common.core.event.OrderCompletedEvent;
import com.manhpham.notification.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Consumer của {@code order.events}: gửi email XÁC NHẬN khi đơn hoàn tất. Binding
 * {@code orderCompletedSink-in-0} (application.properties). Idempotent qua
 * {@link NotificationService} (dedup_key = {@code order-completed:<orderId>}).
 *
 * <p>Email cố ý NEUTRAL ("đang phát vé") vì Ticket phát vé độc lập (cùng consume order.events,
 * thứ tự không đảm bảo). Muốn email kèm vé thật thì cho Notification consume sự kiện
 * {@code TicketsIssued} do Ticket phát — đó là bản nâng cấp.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCompletedSink implements Consumer<OrderCompletedEvent> {

    private final NotificationService notifications;

    @Override
    public void accept(OrderCompletedEvent event) {
        if (event.email() == null || event.email().isBlank()) {
            log.warn("Đơn {} không có email — bỏ qua gửi xác nhận", event.orderId());
            return;
        }
        String body = "Cảm ơn bạn! Đơn " + event.orderId() + " gồm " + event.quantity()
                + " vé (" + event.amountMinor() + " " + event.currency() + ") đã thanh toán thành công. "
                + "Vé đang được phát và sẽ sẵn sàng trong mục \"Vé của tôi\".";
        notifications.sendOnce(
                "order-completed:" + event.orderId(),
                NotificationChannel.EMAIL,
                event.email(),
                "ORDER_CONFIRMATION",
                "Đơn hàng của bạn đã hoàn tất",
                body);
    }
}
