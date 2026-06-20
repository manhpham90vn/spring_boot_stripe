package com.manhpham.auth.scheduler;

import com.manhpham.auth.repositories.jpa.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Cắt bớt các dòng outbox đã được Debezium thu thập để bảng luôn nhỏ gọn. Đây THUẦN TÚY
 * là việc dọn rác — KHÔNG phát đi cái gì (Debezium làm việc đó, đọc từ WAL). An toàn để
 * chạy độc lập với tiến độ của connector: dòng đã commit thì đã nằm trong WAL, mà
 * replication slot giữ WAL lại cho tới khi Debezium tiêu thụ xong → không sợ mất event.
 */
@Component
@Slf4j
public class OutboxPurgeJob {

    private final OutboxEventRepository repository;
    private final Duration retention;

    // retention đọc từ property, mặc định "P3D" = 3 ngày (định dạng ISO-8601 Duration).
    // Spring tự parse chuỗi đó thành Duration.
    public OutboxPurgeJob(OutboxEventRepository repository,
                          @Value("${auth.outbox.retention:P3D}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    // Mặc định chạy 3h sáng mỗi ngày. Chỉ DỌN RÁC, không liên quan tới việc phát event:
    // xoá row cũ hơn retention cho bảng outbox khỏi phình. An toàn vì row đã commit nằm
    // trong WAL — replication slot giữ WAL tới khi Debezium đọc xong, không sợ mất event.
    @Scheduled(cron = "${auth.outbox.purge-cron:0 0 3 * * *}")
    @Transactional
    public void purge() {
        int removed = repository.deleteByCreatedAtBefore(Instant.now().minus(retention));
        if (removed > 0) {
            log.info("Purged {} outbox row(s) older than {}", removed, retention);
        }
    }
}
