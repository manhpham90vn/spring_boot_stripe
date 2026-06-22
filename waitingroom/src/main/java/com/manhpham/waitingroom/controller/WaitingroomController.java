package com.manhpham.waitingroom.controller;

import com.manhpham.waitingroom.captcha.CaptchaVerifier;
import com.manhpham.waitingroom.dto.EnqueueRequest;
import com.manhpham.waitingroom.dto.EnqueueResponse;
import com.manhpham.waitingroom.dto.StatusResponse;
import com.manhpham.waitingroom.services.WaitingRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * API CÔNG KHAI của waiting room ({@code /api/waitingroom/public/**} → permit-all ở gateway,
 * xem API-CONVENTIONS.md). Khách vãng lai (chưa đăng nhập) gọi để xếp hàng và poll vị trí.
 */
@RestController
@RequestMapping("/api/waitingroom/public")
@RequiredArgsConstructor
public class WaitingroomController {

    private final WaitingRoomService waitingRoom;
    private final CaptchaVerifier captcha;

    /**
     * Lấy ảnh CAPTCHA. Trả PNG; {@code captchaId} ở header {@code X-Captcha-Id} (client gửi lại
     * kèm con số trong ảnh khi enqueue).
     */
    @GetMapping("/captcha")
    public Mono<ResponseEntity<byte[]>> captcha() {
        return captcha.issue().map(ch -> ResponseEntity.ok()
                .header("X-Captcha-Id", ch.captchaId())
                .contentType(MediaType.IMAGE_PNG)
                .body(ch.png()));
    }

    /** Xin vào hàng: verify CAPTCHA → cấp token + vị trí + ETA. */
    @PostMapping("/{eventId}/enqueue")
    public Mono<EnqueueResponse> enqueue(@PathVariable UUID eventId, @Valid @RequestBody EnqueueRequest request) {
        return waitingRoom.enqueue(eventId, request);
    }

    /** Poll vị trí: còn chờ / tới lượt (kèm PASS) / hết. */
    @GetMapping("/{eventId}/status")
    public Mono<StatusResponse> status(@PathVariable UUID eventId, @RequestParam String token) {
        return waitingRoom.status(eventId, token);
    }
}
