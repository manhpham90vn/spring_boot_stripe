package com.manhpham.waitingroom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Cấu hình van waiting room (xem docs/services/08-waitingroom.md, docs/flows/waiting-room.md).
 *
 * <p>{@code rate} = nhịp thả MẶC ĐỊNH mỗi giây (admission rate) — đặt theo throughput thật của
 * Order/Inventory/Stripe (~100–200 req/s Stripe là trần cứng). Có thể override per-event qua Redis
 * key {@code wr:rate:{eventId}} (internal API).
 *
 * <p>{@code tokenTtl} = thời hạn 1 chỗ trong hàng ({@code wr:token:*}); {@code admitTtl} = thời hạn
 * PASS sau khi được thả ({@code wr:admit:*}) — vào trễ quá hạn phải xếp lại (chống giữ chỗ vô hạn).
 *
 * <p>{@code eventTicketTypes}: ánh xạ TUỲ CHỌN eventId → các ticketTypeId để hỏi tồn kho Inventory.
 * Có mapping → AdmissionJob cộng tồn còn lại, hết thì NGỪNG thả. Không có → dựa vào cờ sold-out
 * (internal API do Order/Inventory bắn sang) + rate. Đây là điểm "admission rate phải biết tồn kho".
 */
@ConfigurationProperties(prefix = "waitingroom")
public record WaitingRoomProperties(
        Admission admission,
        Map<UUID, java.util.List<UUID>> eventTicketTypes) {

    public WaitingRoomProperties {
        if (admission == null) {
            admission = new Admission(20, Duration.ofMinutes(30), Duration.ofMinutes(2));
        }
        if (eventTicketTypes == null) {
            eventTicketTypes = Map.of();
        }
    }

    public record Admission(int rate, Duration tokenTtl, Duration admitTtl) {
        public Admission {
            if (rate <= 0) {
                rate = 20;
            }
            if (tokenTtl == null) {
                tokenTtl = Duration.ofMinutes(30);
            }
            if (admitTtl == null) {
                admitTtl = Duration.ofMinutes(2);
            }
        }
    }
}
