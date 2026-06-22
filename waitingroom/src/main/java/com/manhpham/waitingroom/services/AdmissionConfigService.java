package com.manhpham.waitingroom.services;

import com.manhpham.waitingroom.dto.AdmissionConfig;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Đọc/ghi cấu hình admission động. NGUỒN DUY NHẤT lúc chạy là Redis hash {@code wr:config};
 * {@code application.properties} chỉ dùng để SEED lần đầu (xem {@link #seedIfAbsent()}).
 * Tách riêng để controller admin chỉnh và service mua dùng chung một nguồn.
 */
public interface AdmissionConfigService {

    /** Seed {@code wr:config} từ properties NẾU field còn thiếu (HSETNX — không đè giá trị admin). */
    Mono<Void> seedIfAbsent();

    /** Cấu hình hiện hành — đọc CHỈ từ Redis (tự seed nếu Redis trống). */
    Mono<AdmissionConfig> current();

    /** Cập nhật (admin). Chỉ ghi các trường hợp lệ; trả lại cấu hình sau cập nhật. */
    Mono<AdmissionConfig> update(AdmissionConfig config);

    /** Nhịp thả toàn cục hiện hành (giây⁻¹). */
    Mono<Integer> rate();

    /** TTL của một chỗ trong hàng. */
    Mono<Duration> tokenTtl();

    /** TTL của PASS. */
    Mono<Duration> admitTtl();
}
