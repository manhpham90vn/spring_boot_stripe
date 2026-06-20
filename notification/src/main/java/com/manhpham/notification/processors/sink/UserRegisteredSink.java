package com.manhpham.notification.processors.sink;

import java.util.function.Consumer;

import com.manhpham.notification.event.UserRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Đầu NHẬN của luồng "đăng ký → gửi thông báo". Đây là một consumer Kafka viết theo kiểu
 * Spring Cloud Stream functional: chỉ cần khai một {@link Consumer}, framework tự đấu nó
 * vào topic.
 *
 * <p>Cơ chế binding: tên bean ({@code userRegisteredSink}) + hậu tố {@code -in-0} tạo thành
 * binding {@code userRegisteredSink-in-0}; trong application.properties ta trỏ binding đó
 * tới topic {@code user.events}, và khai {@code spring.cloud.function.definition} =
 * {@code userRegisteredSink} để Spring biết hàm nào là consumer. Mỗi message tới, framework
 * deserialize JSON thành {@link UserRegisteredEvent} rồi gọi {@link #accept}.
 */
@Component
@Slf4j
public class UserRegisteredSink implements Consumer<UserRegisteredEvent> {

    private final JavaMailSender mailSender;
    private final String from;

    public UserRegisteredSink(JavaMailSender mailSender,
                              @Value("${notification.mail.from:no-reply@tickets.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    // Được gọi cho MỖI message đọc từ topic. event đã được framework deserialize sẵn từ JSON.
    @Override
    public void accept(UserRegisteredEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject("Welcome to the ticket platform");
        message.setText("Your account (" + event.email() + ") is ready. Happy ticket hunting!");

        try {
            mailSender.send(message);
            log.info("Sent welcome email for userId={}", event.userId());
        } catch (MailException e) {
            // "Nuốt" lỗi để khi SMTP dev hỏng không gây vòng lặp gửi lại liên tục (hot-loop):
            // nếu ném exception, Spring Cloud Stream sẽ redeliver mãi. Ở môi trường thật cần
            // gắn Kafka DLQ (dead-letter queue) + retry có kiểm soát trước khi dựa vào cách này.
            log.error("Failed to send welcome email for userId={}: {}", event.userId(), e.getMessage());
        }
    }
}
