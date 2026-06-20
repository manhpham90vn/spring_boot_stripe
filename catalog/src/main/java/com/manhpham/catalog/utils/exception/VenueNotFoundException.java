package com.manhpham.catalog.utils.exception;

import java.util.UUID;

/** Ném khi không tìm thấy địa điểm theo id. GlobalExceptionHandler ánh xạ sang HTTP 404. */
public class VenueNotFoundException extends RuntimeException {
    public VenueNotFoundException(UUID id) {
        super("Venue not found: " + id);
    }
}
