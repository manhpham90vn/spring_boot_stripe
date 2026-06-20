package com.manhpham.ticket.controller;

import com.manhpham.ticket.dto.ValidateRequest;
import com.manhpham.ticket.dto.ValidateResponse;
import com.manhpham.ticket.services.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API cho THIẾT BỊ CỔNG quét vé. Đặt dưới {@code /internal/**} (thiết bị cổng là hệ nội bộ,
 * không phải client công khai) — rào bằng mạng, không JWT người dùng. Xem API-CONVENTIONS.md.
 */
@RestController
@RequestMapping("/internal/tickets")
@RequiredArgsConstructor
public class InternalTicketController {

    private final TicketService tickets;

    /** Quét QR tại cổng: verify chữ ký + đánh dấu USED. */
    @PostMapping("/validate")
    public ValidateResponse validate(@Valid @RequestBody ValidateRequest request) {
        return tickets.validate(request.qrToken());
    }
}
