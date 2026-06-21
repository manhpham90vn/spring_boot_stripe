package com.manhpham.catalog.utils.exception;

import java.util.UUID;

/** Ném khi không tìm thấy ghế (seat_map) theo id. GlobalExceptionHandler ánh xạ sang HTTP 404. */
public class SeatNotFoundException extends RuntimeException {
    public SeatNotFoundException(UUID id) {
        super("Seat not found: " + id);
    }
}
