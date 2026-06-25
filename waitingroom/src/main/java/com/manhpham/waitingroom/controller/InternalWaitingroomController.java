package com.manhpham.waitingroom.controller;

import com.manhpham.waitingroom.dto.AdmissionCheckResponse;
import com.manhpham.waitingroom.services.WaitingRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * API NỘI BỘ ({@code /internal/**}: service↔service, KHÔNG qua gateway, rào bằng NetworkPolicy —
 * xem API-CONVENTIONS.md). Order/Gateway verify PASS trước khi nhận đặt đơn; Order/Inventory bắn
 * cờ sold-out; vận hành chỉnh nhịp thả per-event.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalWaitingroomController {

    private final WaitingRoomService waitingRoom;

    /** Verify PASS: hạ nguồn (Order) gọi trước khi cho {@code POST /api/order}. */
    @GetMapping("/admission/{eventId}/check")
    public Mono<AdmissionCheckResponse> check(@PathVariable UUID eventId, @RequestParam String token) {
        return waitingRoom.isAdmitted(eventId, token).map(admitted -> new AdmissionCheckResponse(admitted));
    }

    /** Đặt nhịp thả per-event (theo throughput thật của Order/Inventory/Stripe). */
    @PostMapping("/events/{eventId}/rate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> setRate(@PathVariable UUID eventId, @RequestParam int rate) {
        return waitingRoom.setRate(eventId, rate);
    }

    /** Đánh dấu hết vé → admission ngừng thả (Inventory/Order gọi khi sold-out). */
    @PostMapping("/events/{eventId}/soldout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> markSoldOut(@PathVariable UUID eventId) {
        return waitingRoom.markSoldOut(eventId);
    }
}
