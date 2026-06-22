package com.manhpham.waitingroom.scheduler;

import com.manhpham.waitingroom.services.WaitingRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Bộ thả (drip) định kỳ: mỗi nhịp duyệt các event đang có hàng và thả top-N theo admission rate
 * (xem docs/flows/waiting-room.md §2-3). Chạy mỗi giây để rate ~= "số người thả mỗi giây".
 *
 * <p>Chạy trên thread scheduler (KHÔNG phải event-loop Netty) nên kết thúc chuỗi reactive bằng
 * {@code blockLast} là chấp nhận được. Mỗi nhịp độc lập & idempotent — lỡ một nhịp không sao.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdmissionJob {

    private final WaitingRoomService waitingRoom;

    @Scheduled(fixedDelayString = "${waitingroom.admission.tick:PT1S}")
    public void drip() {
        try {
            waitingRoom.activeEvents()
                    .flatMap(waitingRoom::admitNext)
                    .blockLast(Duration.ofSeconds(10));
        } catch (Exception ex) {
            log.warn("Nhịp admission lỗi (bỏ qua, nhịp sau thử lại): {}", ex.toString());
        }
    }
}
