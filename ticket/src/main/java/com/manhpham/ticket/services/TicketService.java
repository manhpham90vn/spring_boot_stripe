package com.manhpham.ticket.services;

import com.manhpham.ticket.dto.TicketResponse;
import com.manhpham.ticket.dto.ValidateResponse;
import com.manhpham.ticket.entities.IssuedTicket;
import com.manhpham.ticket.event.OrderCompletedEvent;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    /** Phát vé cho một đơn đã hoàn tất (idempotent — Kafka at-least-once). */
    void issueForOrder(OrderCompletedEvent event);

    /** Vé của một người. */
    List<TicketResponse> myTickets(UUID userId);

    /** Lấy một vé thuộc về người gọi (cho QR ảnh); 404 nếu không phải của họ. */
    IssuedTicket getOwned(UUID userId, UUID ticketId);

    /** Kiểm vé tại cổng: verify chữ ký + còn VALID → đánh dấu USED. */
    ValidateResponse validate(String qrToken);
}
