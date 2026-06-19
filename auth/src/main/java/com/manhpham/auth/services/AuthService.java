package com.manhpham.auth.services;

import com.manhpham.auth.dto.LoginRequest;
import com.manhpham.auth.dto.RegisterRequest;
import com.manhpham.auth.dto.TokenResponse;
import com.manhpham.auth.entities.User;

import java.util.UUID;

public interface AuthService {

    User register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    /** Load the user behind a verified token's {@code sub} claim. */
    User currentUser(UUID userId);
}
