package com.manhpham.auth.utils.exception;

/**
 * Ném khi đăng nhập thất bại: email không tồn tại, tài khoản bị khóa, hoặc sai mật khẩu.
 * Cố ý GỘP CHUNG mọi nguyên nhân vào một thông điệp mơ hồ để không lộ email nào đã tồn
 * tại (chống dò tài khoản). GlobalExceptionHandler ánh xạ sang HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
