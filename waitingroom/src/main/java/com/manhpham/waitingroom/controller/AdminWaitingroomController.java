package com.manhpham.waitingroom.controller;

import com.manhpham.waitingroom.dto.AdmissionConfig;
import com.manhpham.waitingroom.services.AdmissionConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * API QUẢN TRỊ ({@code /api/waitingroom/admin/**}): chỉnh cấu hình admission ĐỘNG từ trang admin
 * (rate, TTL) — lưu Redis, áp dụng ngay không cần redeploy. Cần JWT + role ADMIN (xem SecurityConfig).
 */
@RestController
@RequestMapping("/api/waitingroom/admin")
@RequiredArgsConstructor
public class AdminWaitingroomController {

    private final AdmissionConfigService config;

    /** Cấu hình admission hiện hành (Redis override + mặc định). */
    @GetMapping("/config")
    public Mono<AdmissionConfig> get() {
        return config.current();
    }

    /** Cập nhật cấu hình admission. */
    @PutMapping("/config")
    public Mono<AdmissionConfig> update(@Valid @RequestBody UpdateConfigRequest request) {
        return config.update(new AdmissionConfig(
                request.rate(), request.tokenTtlSeconds(), request.admitTtlSeconds()));
    }

    /** Body cập nhật — mọi giá trị phải dương (chống cấu hình làm tê liệt admission). */
    public record UpdateConfigRequest(
            @Positive(message = "rate phải > 0") int rate,
            @Positive(message = "tokenTtlSeconds phải > 0") long tokenTtlSeconds,
            @Positive(message = "admitTtlSeconds phải > 0") long admitTtlSeconds) {
    }
}
