package com.manhpham.auth.dto;

import com.manhpham.auth.entities.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }
}
