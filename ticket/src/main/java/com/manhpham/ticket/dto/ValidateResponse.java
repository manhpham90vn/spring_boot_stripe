package com.manhpham.ticket.dto;

import java.util.UUID;

/** Kết quả kiểm vé tại cổng. */
public record ValidateResponse(boolean valid, String message, UUID ticketId, UUID eventId) {

    public static ValidateResponse ok(UUID ticketId, UUID eventId) {
        return new ValidateResponse(true, "Hợp lệ — cho vào", ticketId, eventId);
    }

    public static ValidateResponse invalid(String message) {
        return new ValidateResponse(false, message, null, null);
    }
}
