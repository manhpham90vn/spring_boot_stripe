package com.manhpham.auth.event;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYLOAD nghiệp vụ của biến cố "user vừa đăng ký" — chính là phần JSON sẽ được lưu vào
 * cột {@code payload} của bảng outbox rồi đẩy lên Kafka. Dùng {@code record} (bất biến)
 * vì event là dữ liệu chỉ-đọc, mô tả một sự việc ĐÃ xảy ra.
 *
 * <p>Chỉ đưa thông tin cần cho consumer (Notification gửi email chào mừng...): id + email
 * + thời điểm xảy ra. KHÔNG bao giờ kèm password hash hay dữ liệu nhạy cảm vào event.
 */
public record UserRegisteredEvent(UUID userId, String email, Instant occurredAt) {

    /** Factory tiện dụng: tự đóng dấu thời điểm xảy ra là "bây giờ". */
    public static UserRegisteredEvent of(UUID userId, String email) {
        return new UserRegisteredEvent(userId, email, Instant.now());
    }
}
