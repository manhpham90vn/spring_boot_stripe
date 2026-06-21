package com.manhpham.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

/**
 * Khởi tạo/đặt lại tồn cho một loại vé (admin/seed). ticketTypeId nằm ở path.
 * GA: gửi {@code totalQty}. SEATED: gửi {@code seatIds} (= Catalog.seat_map.id).
 */
public record SeedStockRequest(
        @NotNull UUID eventId,
        @PositiveOrZero Integer totalQty,
        List<UUID> seatIds) {

    public boolean seated() {
        return seatIds != null && !seatIds.isEmpty();
    }
}
