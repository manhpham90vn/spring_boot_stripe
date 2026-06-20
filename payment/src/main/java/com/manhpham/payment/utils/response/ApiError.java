package com.manhpham.payment.utils.response;

import java.time.Instant;
import java.util.Map;

/** Khuôn lỗi thống nhất. */
public record ApiError(Instant timestamp, int status, String error, String message,
                       Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, null);
    }

    public static ApiError validation(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
