package com.manhpham.auth.utils.exception;

/** Raised when registration is attempted with an email that already exists. */
public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("Email already registered: " + email);
    }
}
