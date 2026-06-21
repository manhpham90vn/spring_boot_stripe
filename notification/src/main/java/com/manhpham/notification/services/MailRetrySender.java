package com.manhpham.notification.services;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gửi email với {@code @Retry} backoff (cấu hình {@code resilience4j.retry.instances.mail}).
 * Tách thành bean RIÊNG để annotation AOP được weave (gọi từ {@link NotificationService},
 * không phải self-invocation). Lỗi SMTP ({@link org.springframework.mail.MailException}) được
 * retry; hết lần thì ném ra để service ghi {@code FAILED}.
 */
@Component
@Slf4j
public class MailRetrySender {

    private final JavaMailSender mailSender;
    private final String from;

    public MailRetrySender(JavaMailSender mailSender,
                           @Value("${notification.mail.from:no-reply@tickets.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Retry(name = "mail")
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message); // MailException (RuntimeException) → @Retry backoff
    }
}
