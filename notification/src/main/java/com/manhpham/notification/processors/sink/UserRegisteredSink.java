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
 * Inbound side of the registration → notification flow. Bound to the
 * {@code user.events} topic via the {@code userRegisteredSink-in-0} binding
 * (see application.properties + spring.cloud.function.definition) and sends the
 * welcome email.
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
            // Swallow so a dev SMTP outage doesn't hot-loop redelivery; wire a Kafka
            // DLQ + retry before relying on this in a real environment.
            log.error("Failed to send welcome email for userId={}: {}", event.userId(), e.getMessage());
        }
    }
}
