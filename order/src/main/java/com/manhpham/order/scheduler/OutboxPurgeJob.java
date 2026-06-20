package com.manhpham.order.scheduler;

import com.manhpham.order.repositories.jpa.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Dọn các dòng outbox đã được Debezium thu thập (housekeeping). KHÔNG phát event — Debezium
 * làm việc đó từ WAL. An toàn chạy độc lập với tiến độ connector: dòng đã commit nằm trong
 * WAL, replication slot giữ lại tới khi Debezium đọc xong. Xem outbox-debezium.md §6.
 */
@Component
@Slf4j
public class OutboxPurgeJob {

    private final OutboxEventRepository repository;
    private final Duration retention;

    public OutboxPurgeJob(OutboxEventRepository repository,
                          @Value("${order.outbox.retention:P3D}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    // Mặc định 3h sáng mỗi ngày; xóa dòng cũ hơn retention (ISO-8601, mặc định P3D = 3 ngày).
    @Scheduled(cron = "${order.outbox.purge-cron:0 0 3 * * *}")
    @Transactional
    public void purge() {
        int removed = repository.deleteByCreatedAtBefore(Instant.now().minus(retention));
        if (removed > 0) {
            log.info("Purged {} outbox row(s) older than {}", removed, retention);
        }
    }
}
