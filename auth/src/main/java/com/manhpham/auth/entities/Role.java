package com.manhpham.auth.entities;

/**
 * Vai trò của tài khoản. Giá trị này được nhồi vào claim {@code roles} của JWT khi
 * phát token (JwtServiceImpl), rồi resource-server đổi {@code USER} → authority
 * {@code ROLE_USER} để phân quyền (vd {@code @PreAuthorize("hasRole('ADMIN')")}).
 * Giữ gọn 2 role ở giai đoạn đầu; thêm role mới chỉ cần bổ sung hằng số ở đây.
 */
public enum Role {
    USER,
    ADMIN
}
