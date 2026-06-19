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
 * Trims already-captured outbox rows so the table stays small. This is pure
 * housekeeping — it does NOT publish anything (Debezium does, off the WAL). Safe
 * to run independently of the connector's progress: a committed row is already in
 * the WAL, which the replication slot retains until Debezium has consumed it.
 */
@Component
@Slf4j
public class OutboxPurgeJob {

    private final OutboxEventRepository repository;
    private final Duration retention;

    public OutboxPurgeJob(OutboxEventRepository repository,
                          @Value("${auth.outbox.retention:P3D}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(cron = "${auth.outbox.purge-cron:0 0 3 * * *}")
    @Transactional
    public void purge() {
        int removed = repository.deleteByCreatedAtBefore(Instant.now().minus(retention));
        if (removed > 0) {
            log.info("Purged {} outbox row(s) older than {}", removed, retention);
        }
    }
}
