package com.manhpham.notification.processors.sink;

import java.util.function.Consumer;

import com.manhpham.notification.entities.NotificationChannel;
import com.manhpham.notification.event.UserRegisteredEvent;
import com.manhpham.notification.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka (Spring Cloud Stream functional) của {@code user.events}: gửi email CHÀO MỪNG
 * khi có user đăng ký. Binding {@code userRegisteredSink-in-0} → topic {@code user.events}
 * (xem application.properties). Idempotent qua {@link NotificationService}
 * (dedup_key = {@code welcome:<userId>}) → Kafka at-least-once không gửi trùng.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredSink implements Consumer<UserRegisteredEvent> {

    private final NotificationService notifications;

    @Override
    public void accept(UserRegisteredEvent event) {
        if (event.email() == null || event.email().isBlank()) {
            log.warn("UserRegistered {} không có email — bỏ qua welcome", event.userId());
            return;
        }
        notifications.sendOnce(
                "welcome:" + event.userId(),
                NotificationChannel.EMAIL,
                event.email(),
                "WELCOME",
                "Welcome to the ticket platform",
                "Your account (" + event.email() + ") is ready. Happy ticket hunting!");
    }
}
