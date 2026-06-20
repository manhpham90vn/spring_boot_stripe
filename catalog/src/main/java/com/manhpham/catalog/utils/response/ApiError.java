package com.manhpham.catalog.utils.response;

import java.time.Instant;
import java.util.Map;

/**
 * Khuôn lỗi THỐNG NHẤT của Catalog (giống ý tưởng ApiError ở Auth). Trả lỗi theo cấu trúc
 * cố định để client parse/hiển thị nhất quán. {@code fieldErrors} chỉ dùng cho lỗi validate.
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

    /** Lỗi validate — kèm map các field bị sai và lý do. */
    public static ApiError validation(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
