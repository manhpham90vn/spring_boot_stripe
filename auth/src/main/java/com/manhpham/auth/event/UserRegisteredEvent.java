package com.manhpham.auth.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String email, Instant occurredAt) {

    public static UserRegisteredEvent of(UUID userId, String email) {
        return new UserRegisteredEvent(userId, email, Instant.now());
    }
}
