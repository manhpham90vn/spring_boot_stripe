package com.manhpham.waitingroom.services.impl;

import com.manhpham.waitingroom.config.WaitingRoomProperties;
import com.manhpham.waitingroom.dto.AdmissionConfig;
import com.manhpham.waitingroom.services.AdmissionConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Cấu hình admission ĐỘNG. NGUỒN DUY NHẤT lúc chạy là Redis hash {@code wr:config} — admin chỉnh
 * qua đây, áp dụng ngay, KHÔNG cần PostgreSQL (đúng ràng buộc kiến trúc) và không redeploy.
 *
 * <p>{@link WaitingRoomProperties} (application.properties) CHỈ là giá trị seed lần đầu: lúc khởi
 * động {@link #seedIfAbsent()} ghi vào Redis bằng {@code HSETNX} (chỉ field còn thiếu, không bao
 * giờ đè giá trị admin đã sửa). Sau đó mọi lần đọc đều lấy thẳng từ Redis — không merge lại
 * properties, nên cấu hình "sống" nằm ở đúng một nơi: Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionConfigServiceImpl implements AdmissionConfigService {

    static final String CONFIG_KEY = "wr:config";
    static final String F_RATE = "rate";
    static final String F_TOKEN_TTL = "tokenTtlSeconds";
    static final String F_ADMIT_TTL = "admitTtlSeconds";

    private final ReactiveStringRedisTemplate redis;
    private final WaitingRoomProperties props;

    @Override
    public Mono<Void> seedIfAbsent() {
        WaitingRoomProperties.Admission def = props.admission();
        var ops = redis.<String, String>opsForHash();
        // HSETNX từng field: chỉ điền field CHƯA có → seed lần đầu + backfill field mới, không đè admin.
        return Mono.zip(
                        ops.putIfAbsent(CONFIG_KEY, F_RATE, String.valueOf(def.rate())),
                        ops.putIfAbsent(CONFIG_KEY, F_TOKEN_TTL, String.valueOf(def.tokenTtl().toSeconds())),
                        ops.putIfAbsent(CONFIG_KEY, F_ADMIT_TTL, String.valueOf(def.admitTtl().toSeconds())))
                .doOnNext(seeded -> {
                    if (Boolean.TRUE.equals(seeded.getT1()) || Boolean.TRUE.equals(seeded.getT2())
                            || Boolean.TRUE.equals(seeded.getT3())) {
                        log.info("Seed wr:config từ properties (field còn thiếu): rate={} tokenTtl={}s admitTtl={}s",
                                def.rate(), def.tokenTtl().toSeconds(), def.admitTtl().toSeconds());
                    }
                })
                .then();
    }

    @Override
    public Mono<AdmissionConfig> current() {
        return readHash().flatMap(h -> {
            if (h.containsKey(F_RATE) && h.containsKey(F_TOKEN_TTL) && h.containsKey(F_ADMIT_TTL)) {
                return Mono.just(toConfig(h));
            }
            // Redis trống/thiếu (vd vừa bị flush) → seed lại rồi đọc. Nguồn vẫn luôn là Redis.
            return seedIfAbsent().then(readHash()).map(this::toConfig);
        });
    }

    private Mono<Map<String, String>> readHash() {
        return redis.<String, String>opsForHash().entries(CONFIG_KEY)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /** Map hash Redis → config. Sau seed các field luôn có; parse lỗi thì rơi về mặc định properties (phòng hờ). */
    private AdmissionConfig toConfig(Map<String, String> h) {
        WaitingRoomProperties.Admission def = props.admission();
        int rate = parseInt(h.get(F_RATE), def.rate());
        long tokenTtl = parseLong(h.get(F_TOKEN_TTL), def.tokenTtl().toSeconds());
        long admitTtl = parseLong(h.get(F_ADMIT_TTL), def.admitTtl().toSeconds());
        return new AdmissionConfig(rate, tokenTtl, admitTtl);
    }

    @Override
    public Mono<AdmissionConfig> update(AdmissionConfig config) {
        // Chỉ nhận giá trị dương; chặn cấu hình vô lý (rate<=0 sẽ làm tê liệt admission).
        if (config.rate() <= 0 || config.tokenTtlSeconds() <= 0 || config.admitTtlSeconds() <= 0) {
            return Mono.error(new IllegalArgumentException("rate/tokenTtlSeconds/admitTtlSeconds phải > 0"));
        }
        Map<String, String> values = Map.of(
                F_RATE, String.valueOf(config.rate()),
                F_TOKEN_TTL, String.valueOf(config.tokenTtlSeconds()),
                F_ADMIT_TTL, String.valueOf(config.admitTtlSeconds()));
        return redis.opsForHash().putAll(CONFIG_KEY, values)
                .doOnSuccess(v -> log.info("Cập nhật admission config: {}", config))
                .then(current());
    }

    @Override
    public Mono<Integer> rate() {
        return current().map(AdmissionConfig::rate);
    }

    @Override
    public Mono<Duration> tokenTtl() {
        return current().map(c -> Duration.ofSeconds(c.tokenTtlSeconds()));
    }

    @Override
    public Mono<Duration> admitTtl() {
        return current().map(c -> Duration.ofSeconds(c.admitTtlSeconds()));
    }

    private static int parseInt(String v, int fallback) {
        try {
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String v, long fallback) {
        try {
            return v == null ? fallback : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
