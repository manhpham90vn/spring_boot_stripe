package com.manhpham.order.repositories.jpa;

import com.manhpham.order.entities.Order;
import com.manhpham.order.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Đơn kẹt ở một trạng thái quá lâu — cho reconciliation (vd AWAITING_PAYMENT bị mất event). */
    List<Order> findByStatusAndUpdatedAtBefore(OrderStatus status, Instant cutoff);
}
