package com.manhpham.catalog.utils.exception;

import java.util.UUID;

/** Ném khi không tìm thấy sự kiện theo id. GlobalExceptionHandler ánh xạ sang HTTP 404. */
public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(UUID id) {
        super("Event not found: " + id);
    }
}
