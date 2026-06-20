package com.manhpham.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Dữ liệu đầu vào cho đăng nhập. Dùng {@code record} cho DTO bất biến, gọn nhẹ.
 * {@code @NotBlank} = không null và không rỗng/toàn khoảng trắng. Lưu ý: KHÔNG kiểm tra
 * {@code @Email} hay độ dài ở đây — đăng nhập chỉ cần "có nhập gì đó"; việc đúng/sai để
 * tầng service đối chiếu, tránh rò rỉ rằng email phải đúng định dạng mới qua được.
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password) {
}
