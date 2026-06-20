package com.manhpham.ticket.utils.exception;

import java.util.UUID;

/** Vé không tồn tại hoặc không thuộc người gọi (404 để không lộ — chống IDOR). */
public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(UUID id) {
        super("Ticket not found: " + id);
    }
}
