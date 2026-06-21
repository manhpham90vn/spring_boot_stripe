package com.manhpham.notification.services.impl;

import com.manhpham.notification.entities.NotificationChannel;
import com.manhpham.notification.entities.SentNotification;
import com.manhpham.notification.repositories.jpa.SentNotificationRepository;
import com.manhpham.notification.services.MailRetrySender;
import com.manhpham.notification.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final SentNotificationRepository sent;
    private final MailRetrySender mailSender;

    @Override
    @Transactional
    public void sendOnce(String dedupKey, NotificationChannel channel, String recipient,
                         String template, String subject, String body) {
        // Idempotent: đã gửi THÀNH CÔNG rồi → bỏ qua (Kafka at-least-once có thể giao lại).
        SentNotification record = sent.findByDedupKey(dedupKey).orElse(null);
        if (record != null && record.isSent()) {
            log.info("dedup hit {} → bỏ qua (đã gửi)", dedupKey);
            return;
        }
        if (record == null) {
            record = SentNotification.create(dedupKey, channel, recipient, template);
            try {
                // Chốt chỗ sớm: nếu instance khác đang xử lý cùng dedupKey → UNIQUE chặn ⇒ bỏ qua.
                sent.saveAndFlush(record);
            } catch (DataIntegrityViolationException race) {
                log.info("dedup race {} → instance khác đang xử lý, bỏ qua", dedupKey);
                return;
            }
        }

        try {
            mailSender.send(recipient, subject, body); // @Retry backoff bên trong
            record.markSent();
            log.info("Đã gửi {} tới {} (dedup {})", template, recipient, dedupKey);
        } catch (RuntimeException e) {
            // Hết retry vẫn lỗi → ghi FAILED + log, KHÔNG ném (không chặn consumer). Xử lại qua job/DLT.
            record.markFailed(e.getMessage());
            log.error("Gửi {} thất bại (dedup {}): {}", template, dedupKey, e.getMessage());
        }
        sent.save(record);
    }
}
