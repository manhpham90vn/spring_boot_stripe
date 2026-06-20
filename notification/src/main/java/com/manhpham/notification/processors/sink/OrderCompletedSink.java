package com.manhpham.notification.processors.sink;

import java.util.function.Consumer;

import com.manhpham.notification.event.OrderCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Consumer của {@code order.events}: gửi email XÁC NHẬN khi đơn hoàn tất. Binding
 * {@code orderCompletedSink-in-0} (application.properties).
 *
 * <p>Email cố ý NEUTRAL ("đang phát vé") vì Ticket phát vé độc lập (cùng consume order.events,
 * thứ tự không đảm bảo). Muốn email kèm vé thật thì cho Notification consume sự kiện
 * {@code TicketsIssued} do Ticket phát — đó là bản nâng cấp.
 */
@Component
@Slf4j
public class OrderCompletedSink implements Consumer<OrderCompletedEvent> {

    private final JavaMailSender mailSender;
    private final String from;

    public OrderCompletedSink(JavaMailSender mailSender,
                              @Value("${notification.mail.from:no-reply@tickets.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void accept(OrderCompletedEvent event) {
        if (event.email() == null || event.email().isBlank()) {
            log.warn("Đơn {} không có email — bỏ qua gửi xác nhận", event.orderId());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject("Đơn hàng của bạn đã hoàn tất");
        message.setText("Cảm ơn bạn! Đơn " + event.orderId() + " gồm " + event.quantity()
                + " vé (" + event.amountMinor() + " " + event.currency() + ") đã thanh toán thành công. "
                + "Vé đang được phát và sẽ sẵn sàng trong mục \"Vé của tôi\".");
        try {
            mailSender.send(message);
            log.info("Đã gửi email xác nhận đơn {} tới {}", event.orderId(), event.email());
        } catch (MailException e) {
            // Nuốt lỗi để SMTP dev hỏng không gây redeliver vô hạn; prod cần DLQ + retry.
            log.error("Gửi email xác nhận đơn {} thất bại: {}", event.orderId(), e.getMessage());
        }
    }
}
