package com.manhpham.auth.services;

import com.manhpham.auth.entities.User;

/** Phát access token đã ký số cho user đã xác thực. Hiện thực: {@code JwtServiceImpl}. */
public interface JwtService {

    /** Tạo và ký một access token cho user. */
    IssuedToken issueAccessToken(User user);

    /**
     * Kết quả phát token: chuỗi JWT và số giây token còn sống. Tách {@code expiresInSeconds}
     * ra để client biết khi nào cần xin token mới mà không phải tự giải mã JWT.
     */
    record IssuedToken(String token, long expiresInSeconds) {
    }
}
