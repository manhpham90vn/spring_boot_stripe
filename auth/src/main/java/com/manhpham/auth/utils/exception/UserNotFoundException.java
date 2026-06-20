package com.manhpham.auth.utils.exception;

import java.util.UUID;

/**
 * Ném khi claim {@code sub} của một token (đã verify) không còn trỏ tới user nào tồn tại
 * — vd user đã bị xóa sau khi token được phát. GlobalExceptionHandler ánh xạ sang HTTP 404.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
