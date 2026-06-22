package com.manhpham.waitingroom.services.impl;

import com.manhpham.waitingroom.captcha.CaptchaVerifier;
import com.manhpham.waitingroom.client.InventoryClient;
import com.manhpham.waitingroom.services.AdmissionConfigService;
import com.manhpham.waitingroom.dto.EnqueueRequest;
import com.manhpham.waitingroom.dto.EnqueueResponse;
import com.manhpham.waitingroom.dto.EventStats;
import com.manhpham.waitingroom.dto.StatusResponse;
import com.manhpham.waitingroom.services.WaitingRoomService;
import com.manhpham.waitingroom.utils.exception.CaptchaFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Cài đặt van waiting room trên Redis (xem docs/flows/waiting-room.md). Mọi thao tác reactive
 * (ReactiveStringRedisTemplate) — KHÔNG chẹn event-loop khi flash sale.
 *
 * <h3>Keyspace</h3>
 * <pre>
 *   wr:events                       SET   eventId đang có hàng (AdmissionJob duyệt)
 *   wr:seq:{eventId}                INT   bộ đếm tăng dần → score ZSET (FIFO công bằng, không đụng độ)
 *   wr:queue:{eventId}              ZSET  member=token, score=seq ; vị trí = ZRANK
 *   wr:token:{token}                STR   = eventId (TTL) — định danh 1 chỗ trong hàng
 *   wr:admit:{eventId}:{token}      STR   (TTL) — PASS "đã được vào", cho phép /api/order
 *   wr:rate:{eventId}               INT   nhịp thả/giây (override mặc định)
 *   wr:soldout:{eventId}            STR   cờ hết vé → ngừng thả
 *   wr:stats:admitted:{eventId}     INT   TỔNG đã thả (cộng dồn) — chỉ phục vụ thống kê admin
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitingRoomServiceImpl implements WaitingRoomService {

    private static final String EVENTS = "wr:events";

    private final ReactiveStringRedisTemplate redis;
    private final CaptchaVerifier captcha;
    private final InventoryClient inventory;
    private final AdmissionConfigService config;

    private static String queueKey(UUID eventId)  { return "wr:queue:" + eventId; }
    private static String seqKey(UUID eventId)    { return "wr:seq:" + eventId; }
    private static String tokenKey(String token)  { return "wr:token:" + token; }
    private static String rateKey(UUID eventId)   { return "wr:rate:" + eventId; }
    private static String soldOutKey(UUID eventId){ return "wr:soldout:" + eventId; }
    private static String admittedStatKey(UUID eventId) { return "wr:stats:admitted:" + eventId; }
    private static String admitKey(UUID eventId, String token) { return "wr:admit:" + eventId + ":" + token; }

    @Override
    public Mono<EnqueueResponse> enqueue(UUID eventId, EnqueueRequest request) {
        return captcha.verify(request.captchaId(), request.captchaAnswer())
                .flatMap(ok -> {
                    if (Boolean.FALSE.equals(ok)) {
                        return Mono.error(new CaptchaFailedException("CAPTCHA không hợp lệ"));
                    }
                    String token = UUID.randomUUID().toString();
                    // score tăng dần (INCR) → FIFO công bằng, tránh đụng độ score như dùng timestamp.
                    return config.tokenTtl().flatMap(tokenTtl -> redis.opsForValue().increment(seqKey(eventId))
                            .flatMap(seq -> redis.opsForZSet().add(queueKey(eventId), token, seq))
                            .then(redis.opsForSet().add(EVENTS, eventId.toString()))
                            // wr:token giữ TTL: chỗ trong hàng cũng có hạn (chống rác khi client biến mất).
                            .then(redis.opsForValue().set(tokenKey(token), eventId.toString(), tokenTtl))
                            .then(rankPosition(eventId, token))
                            .zipWith(effectiveRate(eventId))
                            .map(t -> new EnqueueResponse(token, t.getT1(), eta(t.getT1(), t.getT2()))));
                });
    }

    @Override
    public Mono<StatusResponse> status(UUID eventId, String token) {
        // PASS thắng tuyệt đối: đã được thả thì trả PASS kể cả khi event đã sold-out.
        return redis.hasKey(admitKey(eventId, token))
                .flatMap(admitted -> {
                    if (Boolean.TRUE.equals(admitted)) {
                        return Mono.just(StatusResponse.admitted(token));
                    }
                    return rankPosition(eventId, token).flatMap(pos -> {
                        if (pos > 0) {
                            return Mono.just(StatusResponse.waiting(pos));
                        }
                        // Không trong hàng & chưa có PASS → hết vé hoặc token hết hạn → dừng poll.
                        return Mono.just(StatusResponse.soldOutResponse());
                    });
                });
    }

    @Override
    public Mono<Boolean> isAdmitted(UUID eventId, String token) {
        if (token == null || token.isBlank()) {
            return Mono.just(false);
        }
        return redis.hasKey(admitKey(eventId, token));
    }

    @Override
    public Mono<Void> setRate(UUID eventId, int ratePerSecond) {
        int rate = Math.max(1, ratePerSecond);
        return redis.opsForValue().set(rateKey(eventId), String.valueOf(rate)).then();
    }

    @Override
    public Mono<Void> markSoldOut(UUID eventId) {
        log.info("Event {} đánh dấu SOLD-OUT → ngừng thả", eventId);
        return redis.opsForValue().set(soldOutKey(eventId), "1").then();
    }

    @Override
    public Mono<Long> admitNext(UUID eventId) {
        return redis.hasKey(soldOutKey(eventId))
                .flatMap(soldOut -> {
                    if (Boolean.TRUE.equals(soldOut)) {
                        return Mono.just(0L); // hết vé → ngừng thả (người còn lại nhận sold-out khi poll)
                    }
                    // Admission rate phải BIẾT tồn kho: hết thì đánh dấu sold-out & dừng.
                    return inventory.hasStock(eventId).flatMap(hasStock -> {
                        if (Boolean.FALSE.equals(hasStock)) {
                            return markSoldOut(eventId).thenReturn(0L);
                        }
                        return effectiveRate(eventId).flatMap(rate -> drip(eventId, rate));
                    });
                });
    }

    @Override
    public Flux<UUID> activeEvents() {
        return redis.opsForSet().members(EVENTS).map(UUID::fromString);
    }

    @Override
    public Mono<EventStats> stats(UUID eventId) {
        Mono<Long> waiting = redis.opsForZSet().size(queueKey(eventId)).defaultIfEmpty(0L);
        Mono<Long> admitted = redis.opsForValue().get(admittedStatKey(eventId))
                .map(Long::parseLong).defaultIfEmpty(0L);
        Mono<Boolean> soldOut = redis.hasKey(soldOutKey(eventId));
        return Mono.zip(waiting, admitted, effectiveRate(eventId), soldOut)
                .map(t -> new EventStats(
                        eventId.toString(),
                        t.getT1(),                 // đang chờ
                        t.getT2(),                 // tổng đã thả
                        t.getT3(),                 // nhịp thả hiệu lực
                        eta(t.getT1(), t.getT3()), // ETA rút cạn hàng
                        t.getT4()));               // sold-out
    }

    @Override
    public Flux<EventStats> allStats() {
        return activeEvents().flatMap(this::stats);
    }

    /** Lấy top-N (ZPOPMIN) khỏi hàng → cấp PASS (wr:admit, có TTL). Trả số token vừa thả. */
    private Mono<Long> drip(UUID eventId, int rate) {
        return config.admitTtl().flatMap(admitTtl -> redis.opsForZSet().popMin(queueKey(eventId), rate)
                .map(tuple -> tuple.getValue())
                .filter(token -> token != null)
                .flatMap(token -> redis.opsForValue()
                        .set(admitKey(eventId, token), "1", admitTtl)
                        .thenReturn(token))
                .count()
                // Cộng dồn TỔNG đã thả để admin thống kê (chỉ tăng khi thực sự thả được người).
                .flatMap(n -> n > 0
                        ? redis.opsForValue().increment(admittedStatKey(eventId), n).thenReturn(n)
                        : Mono.just(n))
                .doOnNext(n -> { if (n > 0) log.debug("Event {} thả {} người", eventId, n); }));
    }

    /** Vị trí 1-based trong hàng; 0 nếu không còn trong hàng. */
    private Mono<Long> rankPosition(UUID eventId, String token) {
        return redis.opsForZSet().rank(queueKey(eventId), token)
                .map(rank -> rank + 1)
                .defaultIfEmpty(0L);
    }

    /** Nhịp thả hiệu lực: override per-event (wr:rate) nếu có, không thì config động toàn cục. */
    private Mono<Integer> effectiveRate(UUID eventId) {
        return redis.opsForValue().get(rateKey(eventId))
                .map(Integer::parseInt)
                .switchIfEmpty(config.rate());
    }

    private static long eta(long position, int rate) {
        int r = Math.max(1, rate);
        return (position + r - 1) / r; // ceil(position / rate) giây
    }
}
