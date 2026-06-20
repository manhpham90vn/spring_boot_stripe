package com.manhpham.payment.repositories.jpa;

import com.manhpham.payment.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Tra payment theo đơn — nền tảng idempotency (1 payment/đơn). */
    Optional<Payment> findByOrderId(UUID orderId);
}
