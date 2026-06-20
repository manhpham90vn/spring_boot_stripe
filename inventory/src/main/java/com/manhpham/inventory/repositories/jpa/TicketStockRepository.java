package com.manhpham.inventory.repositories.jpa;

import com.manhpham.inventory.entities.TicketStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** CRUD cho tồn kho bền. Khoá chính = ticketTypeId. */
public interface TicketStockRepository extends JpaRepository<TicketStock, UUID> {
}
