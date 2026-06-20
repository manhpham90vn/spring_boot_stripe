package com.manhpham.payment.repositories.jpa;

import com.manhpham.payment.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
