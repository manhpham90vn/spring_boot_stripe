package com.manhpham.ticket.dto;

import com.manhpham.ticket.entities.IssuedTicket;
import com.manhpham.ticket.entities.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/** Vé trả về cho chủ vé. qrToken để hiển thị QR (hoặc gọi endpoint ảnh .png). */
public record TicketResponse(
        UUID id, UUID orderId, UUID eventId, UUID ticketTypeId,
        TicketStatus status, String qrToken, Instant issuedAt) {

    public static TicketResponse from(IssuedTicket t) {
        return new TicketResponse(t.getId(), t.getOrderId(), t.getEventId(), t.getTicketTypeId(),
                t.getStatus(), t.getQrToken(), t.getIssuedAt());
    }
}
