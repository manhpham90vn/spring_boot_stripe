package com.manhpham.auth.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
