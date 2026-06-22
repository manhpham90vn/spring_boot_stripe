package com.manhpham.common.core.event;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYLOAD nghiệp vụ của biến cố "user vừa đăng ký" — phần JSON lưu vào cột {@code payload}
 * của bảng outbox rồi đẩy lên Kafka. Dùng {@code record} (bất biến) vì event là dữ liệu
 * chỉ-đọc, mô tả một sự việc ĐÃ xảy ra.
 *
 * <p>Đây là CONTRACT dùng chung giữa producer (Auth) và consumer (Notification). Chỉ đưa
 * thông tin cần cho consumer: id + email + thời điểm. KHÔNG kèm password hash hay dữ liệu
 * nhạy cảm vào event.
 */
public record UserRegisteredEvent(UUID userId, String email, Instant occurredAt) {

    /** Factory tiện dụng (producer dùng): tự đóng dấu thời điểm xảy ra là "bây giờ". */
    public static UserRegisteredEvent of(UUID userId, String email) {
        return new UserRegisteredEvent(userId, email, Instant.now());
    }
}
