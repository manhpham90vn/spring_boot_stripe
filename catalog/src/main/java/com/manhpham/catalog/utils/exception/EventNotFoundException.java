package com.manhpham.catalog.utils.exception;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(UUID id) {
        super("Event not found: " + id);
    }
}
