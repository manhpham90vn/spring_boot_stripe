package com.manhpham.catalog.repositories.jpa;

import com.manhpham.catalog.entities.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventIdOrderByPriceMinorAsc(UUID eventId);

    /** Xóa toàn bộ loại vé của một sự kiện (khi hard-delete event DRAFT). */
    void deleteByEventId(UUID eventId);
}
