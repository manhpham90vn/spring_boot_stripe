package com.manhpham.auth.services;

import com.manhpham.auth.entities.User;

/** Issues signed access tokens for authenticated users. */
public interface JwtService {

    IssuedToken issueAccessToken(User user);

    record IssuedToken(String token, long expiresInSeconds) {
    }
}
