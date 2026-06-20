package com.manhpham.order.utils.exception;

import java.util.UUID;

/** Loại vé/sự kiện không tồn tại ở Catalog. → HTTP 404. */
public class TicketTypeUnavailableException extends RuntimeException {
    public TicketTypeUnavailableException(UUID ticketTypeId) {
        super("Ticket type not available: " + ticketTypeId);
    }
}
