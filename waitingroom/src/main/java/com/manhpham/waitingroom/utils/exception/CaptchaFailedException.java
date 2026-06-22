package com.manhpham.waitingroom.utils.exception;

/** CAPTCHA không hợp lệ ở bước enqueue → 400 (chống bot chiếm chỗ). */
public class CaptchaFailedException extends RuntimeException {
    public CaptchaFailedException(String message) {
        super(message);
    }
}
