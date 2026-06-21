package com.manhpham.inventory.repositories.jpa;

import com.manhpham.inventory.entities.SeatInventory;
import com.manhpham.inventory.entities.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {

    long countByTicketTypeId(UUID ticketTypeId);

    long countByTicketTypeIdAndStatus(UUID ticketTypeId, SeatStatus status);
}
