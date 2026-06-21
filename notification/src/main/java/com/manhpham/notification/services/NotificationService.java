package com.manhpham.notification.services;

import com.manhpham.notification.entities.NotificationChannel;

public interface NotificationService {

    /**
     * Gửi MỘT thông báo idempotent theo {@code dedupKey}: nếu đã gửi thành công (SENT) thì bỏ
     * qua (chống Kafka at-least-once gửi trùng). Gửi qua {@link MailRetrySender} (retry backoff);
     * hết retry → ghi {@code FAILED} + log, KHÔNG ném ra (không chặn consumer / không redeliver vô hạn).
     */
    void sendOnce(String dedupKey, NotificationChannel channel, String recipient,
                  String template, String subject, String body);
}
