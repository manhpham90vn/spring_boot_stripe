package com.manhpham.auth.services;

import com.manhpham.auth.dto.LoginRequest;
import com.manhpham.auth.dto.RegisterRequest;
import com.manhpham.auth.dto.TokenResponse;
import com.manhpham.auth.entities.User;

public interface AuthService {

    User register(RegisterRequest request);

    TokenResponse login(LoginRequest request);
}
