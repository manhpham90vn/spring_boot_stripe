package com.manhpham.auth.controller;

import com.manhpham.auth.dto.LoginRequest;
import com.manhpham.auth.dto.RegisterRequest;
import com.manhpham.auth.dto.TokenResponse;
import com.manhpham.auth.dto.UserResponse;
import com.manhpham.auth.entities.User;
import com.manhpham.auth.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint của Auth, đặt dưới tiền tố {@code /api/auth}. Theo quy ước path (xem
 * docs/standards/API-CONVENTIONS.md), DẤU HIỆU public/cần-token nằm NGAY TRÊN path:
 * <ul>
 *   <li>{@code /api/auth/public/**} — CÔNG KHAI (register, login): chưa đăng nhập thì
 *       lấy đâu ra token, nên mở.</li>
 *   <li>{@code /api/auth/me} — CẦN JWT: không nằm dưới {@code /public/} nên mặc định bắt
 *       buộc token.</li>
 * </ul>
 *
 * <p>Controller chỉ làm nhiệm vụ "vào/ra HTTP": nhận request, validate, ủy thác cho
 * {@link AuthService}, rồi map entity thành DTO trả về. KHÔNG để logic nghiệp vụ ở đây.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor // Lombok sinh constructor cho field final -> Spring tự inject AuthService
public class AuthController {

    private final AuthService authService;

    /**
     * Đăng ký tài khoản mới — CÔNG KHAI ({@code /public/}). {@code @Valid} kích hoạt bộ
     * kiểm tra ràng buộc trên {@link RegisterRequest} (email hợp lệ, mật khẩu đủ dài...)
     * TRƯỚC khi vào service; lỗi validate do GlobalExceptionHandler bắt và trả 400. Thành
     * công trả 201 CREATED. Trả {@link UserResponse} (KHÔNG kèm password hash).
     */
    @PostMapping("/public/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return UserResponse.from(user);
    }

    /** Đăng nhập — CÔNG KHAI ({@code /public/}): trả JWT đã ký nếu đúng email + mật khẩu. */
    @PostMapping("/public/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Trả về chính người đang gọi (xác định qua Bearer token). Bắt buộc JWT hợp lệ:
     * resource-server đã verify chữ ký/issuer/hạn token TỪ TRƯỚC khi vào hàm này, nên
     * claim {@code sub} là id user đáng tin — chỉ cần đọc ra, không cần kiểm lại.
     *
     * <p>{@code @AuthenticationPrincipal Jwt}: Spring Security bơm thẳng token đã giải mã
     * vào tham số. Endpoint này cũng đóng vai test xuyên suốt (smoke test): đăng ký →
     * đăng nhập → gọi /me bằng token vừa nhận để chắc cả luồng auth chạy thông.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(authService.currentUser(UUID.fromString(jwt.getSubject())));
    }
}
