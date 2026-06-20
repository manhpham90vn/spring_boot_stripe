package com.manhpham.ticket.repositories.jpa;

import com.manhpham.ticket.entities.IssuedTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssuedTicketRepository extends JpaRepository<IssuedTicket, UUID> {

    /** Idempotency: đã phát vé cho đơn này chưa? (Kafka at-least-once → chống phát trùng.) */
    boolean existsByOrderId(UUID orderId);

    /** Vé của một người (cho GET /api/ticket). */
    List<IssuedTicket> findByUserIdOrderByIssuedAtDesc(UUID userId);
}
