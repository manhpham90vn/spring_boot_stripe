package com.manhpham.payment.repositories.jpa;

import com.manhpham.payment.entities.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
}
