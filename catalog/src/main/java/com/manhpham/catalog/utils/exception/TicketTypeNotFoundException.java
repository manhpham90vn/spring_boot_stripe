package com.manhpham.catalog.utils.exception;

import java.util.UUID;

public class TicketTypeNotFoundException extends RuntimeException {
    public TicketTypeNotFoundException(UUID id) {
        super("Ticket type not found: " + id);
    }
}
