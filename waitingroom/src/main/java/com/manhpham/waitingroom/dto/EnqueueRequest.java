package com.manhpham.waitingroom.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Xin vào hàng đợi. Phải kèm CAPTCHA đã giải: {@code captchaId} (lấy từ {@code GET .../captcha}) +
 * {@code captchaAnswer} (con số người dùng nhập từ ảnh) — chống bot chiếm chỗ hàng loạt.
 * {@code userId} tuỳ chọn (khách vãng lai có thể chưa đăng nhập).
 */
public record EnqueueRequest(
        @NotBlank(message = "captchaId bắt buộc") String captchaId,
        @NotBlank(message = "captchaAnswer bắt buộc (nhập số trong ảnh)") String captchaAnswer,
        String userId) {
}
