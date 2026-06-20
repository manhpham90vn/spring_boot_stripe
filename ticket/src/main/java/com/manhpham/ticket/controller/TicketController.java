package com.manhpham.ticket.controller;

import com.manhpham.ticket.dto.TicketResponse;
import com.manhpham.ticket.entities.IssuedTicket;
import com.manhpham.ticket.qr.QrCodeRenderer;
import com.manhpham.ticket.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Vé của người dùng. {@code /api/ticket/**} — cần JWT (common-security). */
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService tickets;
    private final QrCodeRenderer qrRenderer;

    /** Danh sách vé của chính mình. */
    @GetMapping
    public List<TicketResponse> myTickets(@AuthenticationPrincipal Jwt jwt) {
        return tickets.myTickets(UUID.fromString(jwt.getSubject()));
    }

    /** Ảnh QR PNG của một vé (chỉ chủ vé). Quét ảnh này tại cổng. */
    @GetMapping(value = "/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        IssuedTicket t = tickets.getOwned(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.ok(qrRenderer.pngFor(t.getQrToken()));
    }
}
