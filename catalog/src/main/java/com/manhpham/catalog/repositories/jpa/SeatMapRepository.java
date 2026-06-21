package com.manhpham.catalog.repositories.jpa;

import com.manhpham.catalog.entities.SeatMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeatMapRepository extends JpaRepository<SeatMap, UUID> {

    List<SeatMap> findByTicketTypeId(UUID ticketTypeId);

    void deleteByTicketTypeId(UUID ticketTypeId);

    void deleteByTicketTypeIdIn(List<UUID> ticketTypeIds);
}
