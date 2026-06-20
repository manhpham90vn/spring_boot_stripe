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
     * CHỈ để dọn rác — KHÔNG phải cơ chế phát event. Debezium đọc INSERT từ WAL, nên một
     * khi dòng đã commit thì xóa nó là an toàn bất kể connector có đang chậm hay không
     * (replication slot vẫn giữ WAL cho tới khi Debezium đọc xong). Xóa dòng cũ chỉ nhằm
     * giữ cho bảng outbox khỏi phình to.
     *
     * <p>{@code @Modifying}: báo cho Spring Data đây là câu lệnh ghi (DELETE), không phải
     * SELECT. Trả về số dòng đã xóa.
     */
    @Modifying
    @Query("delete from OutboxEventEntity o where o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
