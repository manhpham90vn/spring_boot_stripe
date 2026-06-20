package com.manhpham.catalog.utils.exception;

import java.util.UUID;

/**
 * Ném khi không tìm thấy loại vé theo id, HOẶC loại vé không thuộc đúng sự kiện đang xét
 * (xem loadTicketTypeOfEvent). GlobalExceptionHandler ánh xạ sang HTTP 404.
 */
public class TicketTypeNotFoundException extends RuntimeException {
    public TicketTypeNotFoundException(UUID id) {
        super("Ticket type not found: " + id);
    }
}
