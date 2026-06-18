package com.manhpham.auth.utils.exception;

/** Raised on login when the email is unknown, disabled, or the password is wrong. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
