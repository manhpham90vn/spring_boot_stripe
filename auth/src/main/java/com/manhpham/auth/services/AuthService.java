package com.manhpham.auth.services;

import com.manhpham.auth.dto.LoginRequest;
import com.manhpham.auth.dto.RegisterRequest;
import com.manhpham.auth.dto.TokenResponse;
import com.manhpham.auth.entities.User;

import java.util.UUID;

/**
 * Hợp đồng (interface) cho nghiệp vụ xác thực. Controller chỉ phụ thuộc vào interface này,
 * còn phần hiện thực nằm ở {@code AuthServiceImpl} → dễ thay/giả lập khi viết test và
 * tuân theo nguyên tắc lập trình hướng abstraction thay vì class cụ thể.
 */
public interface AuthService {

    /** Đăng ký user mới; ném EmailAlreadyUsedException nếu email đã tồn tại. */
    User register(RegisterRequest request);

    /** Xác thực thông tin đăng nhập và trả về access token đã ký. */
    TokenResponse login(LoginRequest request);

    /** Nạp user ứng với claim {@code sub} của một token đã được verify. */
    User currentUser(UUID userId);
}
