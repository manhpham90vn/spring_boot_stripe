package com.manhpham.auth.utils.exception;

import java.util.UUID;

/** Raised when a token's subject no longer maps to an existing user. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
