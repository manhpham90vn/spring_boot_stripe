package com.manhpham.auth.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity người dùng — nguồn sự thật về tài khoản, ánh xạ tới bảng {@code users} trong
 * DB riêng của Auth (database-per-service). Thiết kế theo hướng "entity giàu tính đóng
 * gói": field để private, chỉ mở getter ({@code @Getter}), tạo qua factory
 * {@link #create} thay vì setter tự do → trạng thái luôn hợp lệ ngay từ lúc khởi tạo.
 */
@Entity
@Table(name = "users")
@Getter // chỉ sinh getter (không setter) để hạn chế sửa trạng thái tùy tiện sau khi tạo
public class User {

    // Khóa chính dùng UUID (không phải số tự tăng): an toàn hơn khi lộ ra ngoài (không
    // đoán được id kế tiếp) và không phụ thuộc DB sinh id — tiện cho hệ phân tán.
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    // unique = true: ràng buộc DUY NHẤT ở tầng DB. Đây mới là "chốt chặn" thật chống
    // trùng email khi hai request đăng ký chạy song song (xem AuthServiceImpl.register).
    @Column(nullable = false, unique = true)
    private String email;

    // LƯU HASH của mật khẩu (BCrypt), TUYỆT ĐỐI không lưu mật khẩu thô. Tên cột là
    // password_hash để nhấn mạnh điều đó.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // EnumType.STRING: lưu role dưới dạng chuỗi "USER"/"ADMIN" cho dễ đọc và an toàn khi
    // thêm role mới. Nếu dùng ORDINAL (số thứ tự) thì chèn enum mới giữa danh sách sẽ
    // làm lệch toàn bộ dữ liệu cũ — nên tránh.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    // Cờ khóa/mở tài khoản. login() từ chối user enabled = false (coi như sai thông tin).
    @Column(nullable = false)
    private boolean enabled;

    // Mốc tạo: updatable = false để không bao giờ bị ghi đè sau lần insert đầu.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // Constructor rỗng BẮT BUỘC cho JPA (framework cần nó để dựng lại entity từ DB).
        // Để protected nhằm ngăn code nghiệp vụ lỡ tay tạo User rỗng — hãy dùng create().
    }

    /**
     * Factory tạo user mới. Gom việc khởi tạo vào một chỗ để mọi user sinh ra đều nhất
     * quán (mặc định enabled = true). id/createdAt/updatedAt do callback JPA tự gán bên dưới.
     */
    public static User create(String email, String passwordHash, Role role) {
        User u = new User();
        u.email = email;
        u.passwordHash = passwordHash;
        u.role = role;
        u.enabled = true;
        return u;
    }

    // @PrePersist: JPA gọi NGAY TRƯỚC khi INSERT. Sinh UUID (nếu chưa có) và đặt mốc thời
    // gian tại đây → không cần nhớ set thủ công ở tầng service.
    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    // @PreUpdate: JPA gọi ngay trước mỗi UPDATE → tự cập nhật updatedAt.
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
