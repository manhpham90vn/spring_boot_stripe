package com.manhpham.auth.utils.exception;

/** Ném khi đăng ký bằng email đã tồn tại. GlobalExceptionHandler ánh xạ sang HTTP 409. */
public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("Email already registered: " + email);
    }
}
