package com.manhpham.auth.repositories.jpa;

import com.manhpham.auth.entities.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Housekeeping only — NOT a publisher. Debezium reads INSERTs from the WAL, so
     * once a row is committed it is safe to delete regardless of relay lag (the
     * slot retains the WAL). Old rows are purged just to keep the table small.
     */
    @Modifying
    @Query("delete from OutboxEventEntity o where o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
