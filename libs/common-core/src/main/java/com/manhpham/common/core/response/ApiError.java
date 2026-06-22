package com.manhpham.common.core.response;

import java.time.Instant;
import java.util.Map;

/**
 * Khuôn lỗi THỐNG NHẤT cho mọi lỗi đã được xử lý (xem GlobalExceptionHandler của từng service).
 * Trả lỗi theo một cấu trúc cố định giúp phía client (web/app) parse và hiển thị nhất quán
 * trên TOÀN hệ thống.
 *
 * <p>{@code fieldErrors} chỉ có giá trị với lỗi validate (map field → thông điệp); các
 * lỗi khác để null và Jackson sẽ tự bỏ qua nếu cấu hình bỏ field null.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors) {

    /** Lỗi thông thường (không kèm chi tiết theo field). */
    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, null);
    }

    /** Lỗi validate — kèm theo map các field bị sai và lý do. */
    public static ApiError validation(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
