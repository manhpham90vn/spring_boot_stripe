package com.manhpham.ticket.services.impl;

import com.manhpham.ticket.client.CatalogClient;
import com.manhpham.ticket.dto.TicketResponse;
import com.manhpham.ticket.dto.ValidateResponse;
import com.manhpham.ticket.entities.IssuedTicket;
import com.manhpham.ticket.event.OrderCompletedEvent;
import com.manhpham.ticket.qr.QrSigner;
import com.manhpham.ticket.repositories.jpa.IssuedTicketRepository;
import com.manhpham.ticket.services.TicketService;
import com.manhpham.ticket.utils.exception.TicketNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final IssuedTicketRepository tickets;
    private final QrSigner qrSigner;
    private final CatalogClient catalog;

    @Override
    @Transactional
    public void issueForOrder(OrderCompletedEvent event) {
        // IDEMPOTENT: event có thể tới >1 lần (at-least-once) → phát vé đúng MỘT lần/đơn.
        if (tickets.existsByOrderId(event.orderId())) {
            log.info("Đơn {} đã phát vé trước đó — bỏ qua", event.orderId());
            return;
        }
        if (event.seatIds() != null && !event.seatIds().isEmpty()) {
            // SEATED: 1 vé/ghế, gắn seat_id + seat_label (resolve nhãn từ Catalog).
            for (UUID seatId : event.seatIds()) {
                IssuedTicket t = IssuedTicket.create(
                        event.orderId(), event.userId(), event.eventId(), event.ticketTypeId());
                t.assignSeat(seatId, catalog.getSeat(seatId).label());
                t.assignQrToken(qrSigner.sign(t.getId()));
                tickets.save(t);
            }
            log.info("Đã phát {} vé SEATED cho đơn {} (user {})",
                    event.seatIds().size(), event.orderId(), event.userId());
        } else {
            // GA: phát quantity vé không gắn ghế.
            for (int i = 0; i < event.quantity(); i++) {
                IssuedTicket t = IssuedTicket.create(
                        event.orderId(), event.userId(), event.eventId(), event.ticketTypeId());
                t.assignQrToken(qrSigner.sign(t.getId())); // QR ký số trên ticketId
                tickets.save(t);
            }
            log.info("Đã phát {} vé GA cho đơn {} (user {})",
                    event.quantity(), event.orderId(), event.userId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> myTickets(UUID userId) {
        return tickets.findByUserIdOrderByIssuedAtDesc(userId).stream()
                .map(TicketResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IssuedTicket getOwned(UUID userId, UUID ticketId) {
        return tickets.findById(ticketId)
                .filter(t -> t.getUserId().equals(userId)) // IDOR: chỉ chủ vé
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    @Override
    @Transactional
    public ValidateResponse validate(String qrToken) {
        // (1) Verify chữ ký TRƯỚC — vé giả/không có secret thì loại ngay.
        if (!qrSigner.verify(qrToken)) {
            return ValidateResponse.invalid("Chữ ký QR không hợp lệ");
        }
        IssuedTicket t = tickets.findById(qrSigner.ticketId(qrToken)).orElse(null);
        if (t == null) {
            return ValidateResponse.invalid("Vé không tồn tại");
        }
        // (2) Một vé một lượt vào: đã USED thì từ chối (chống dùng lại).
        if (!t.isValid()) {
            return ValidateResponse.invalid("Vé đã được sử dụng");
        }
        t.markUsed(); // dirty checking persist
        log.info("Vé {} quét vào cổng (event {})", t.getId(), t.getEventId());
        return ValidateResponse.ok(t.getId(), t.getEventId());
    }
}
