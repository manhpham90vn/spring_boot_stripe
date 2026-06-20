package com.manhpham.order.repositories.jpa;

import com.manhpham.order.entities.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Dọn rác — KHÔNG phải publisher. Debezium đọc INSERT từ WAL nên xóa dòng đã commit là
     * an toàn (slot giữ WAL tới khi đọc xong). Chỉ để bảng outbox khỏi phình.
     */
    @Modifying
    @Query("delete from OutboxEventEntity o where o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
