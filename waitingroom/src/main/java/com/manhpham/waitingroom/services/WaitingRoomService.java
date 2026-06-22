package com.manhpham.waitingroom.services;

import com.manhpham.waitingroom.dto.EnqueueRequest;
import com.manhpham.waitingroom.dto.EnqueueResponse;
import com.manhpham.waitingroom.dto.EventStats;
import com.manhpham.waitingroom.dto.StatusResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Van waiting room: xếp hàng (Redis ZSET) + thả vào theo nhịp (admission). Store CHÍNH: Redis. */
public interface WaitingRoomService {

    /** Xin vào hàng: verify CAPTCHA → ZADD → cấp token + vị trí + ETA. */
    Mono<EnqueueResponse> enqueue(UUID eventId, EnqueueRequest request);

    /** Poll: còn chờ (vị trí) / tới lượt (PASS) / hết. */
    Mono<StatusResponse> status(UUID eventId, String token);

    /** Verify PASS cho hạ nguồn (Order/Gateway) trước khi nhận đặt đơn. */
    Mono<Boolean> isAdmitted(UUID eventId, String token);

    /** Đặt nhịp thả per-event (override mặc định) — chỉnh theo throughput thật hạ nguồn. */
    Mono<Void> setRate(UUID eventId, int ratePerSecond);

    /** Đánh dấu sự kiện hết vé → admission NGỪNG thả, người còn lại nhận sold-out. */
    Mono<Void> markSoldOut(UUID eventId);

    /** Một nhịp thả cho 1 event (AdmissionJob gọi). Trả số token vừa được thả. */
    Mono<Long> admitNext(UUID eventId);

    /** Các eventId đang có hàng (AdmissionJob duyệt). */
    reactor.core.publisher.Flux<UUID> activeEvents();

    /** Thống kê hàng chờ 1 event cho trang admin (đang chờ, đã thả, nhịp, ETA, sold-out). */
    Mono<EventStats> stats(UUID eventId);

    /** Thống kê tất cả event đang có hàng — dashboard tổng quan cho admin. */
    reactor.core.publisher.Flux<EventStats> allStats();
}
