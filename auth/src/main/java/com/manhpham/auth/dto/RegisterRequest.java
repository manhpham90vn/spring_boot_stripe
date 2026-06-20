package com.manhpham.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu đầu vào cho đăng ký. Các annotation validate được {@code @Valid} ở controller
 * kích hoạt; vi phạm sẽ thành lỗi 400 (xem GlobalExceptionHandler) — chặn dữ liệu rác
 * ngay tại biên, trước khi chạm tới DB.
 */
public record RegisterRequest(
        // @Email: đúng định dạng email. max = 320 là độ dài tối đa hợp lệ của một địa chỉ email.
        @NotBlank @Email @Size(max = 320) String email,

        // min = 8: mật khẩu tối thiểu 8 ký tự. max = 72 vì BCrypt chỉ băm tối đa 72 byte
        // đầu — đặt giới hạn này để tránh hiểu lầm rằng phần dài hơn cũng được tính.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
