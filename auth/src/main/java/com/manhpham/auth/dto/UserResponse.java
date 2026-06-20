package com.manhpham.auth.dto;

import com.manhpham.auth.entities.User;

import java.util.UUID;

/**
 * DTO biểu diễn user để TRẢ RA NGOÀI. Cố ý chỉ chứa id/email/role và TUYỆT ĐỐI không có
 * passwordHash hay cờ nội bộ — tách entity (dùng trong DB) khỏi dữ liệu phơi ra API là
 * cách chuẩn để tránh vô tình lộ thông tin nhạy cảm.
 */
public record UserResponse(
        UUID id,
        String email,
        String role) {

    /** Chuyển entity {@link User} sang DTO an toàn để trả về (chỉ lấy các field công khai). */
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }
}
