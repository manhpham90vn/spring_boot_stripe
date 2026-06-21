package com.manhpham.catalog.controller;

import com.manhpham.catalog.dto.SeatResponse;
import com.manhpham.catalog.services.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API NỘI BỘ của Catalog ({@code /internal/**}, xem API-CONVENTIONS.md): CHỈ service↔service
 * gọi, KHÔNG route ở gateway. Ticket gọi {@code GET /internal/seats/{seatId}} để resolve nhãn
 * ghế (in lên vé) lúc phát vé SEATED. Rào bằng NetworkPolicy ở prod (không JWT người dùng).
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCatalogController {

    private final EventService eventService;

    @GetMapping("/seats/{seatId}")
    public SeatResponse seat(@PathVariable UUID seatId) {
        return eventService.getSeat(seatId);
    }
}
