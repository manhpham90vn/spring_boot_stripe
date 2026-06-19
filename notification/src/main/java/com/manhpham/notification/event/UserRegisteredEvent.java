package com.manhpham.notification.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound copy of the auth service's UserRegisteredEvent. Field names form the
 * wire contract (deserialized from JSON) — keep them in sync with the producer.
 */
public record UserRegisteredEvent(UUID userId, String email, Instant occurredAt) {
}
