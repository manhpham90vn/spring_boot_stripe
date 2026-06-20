package com.manhpham.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/** Khởi tạo/đặt lại tồn cho một loại vé (admin/seed). ticketTypeId nằm ở path. */
public record SeedStockRequest(
        @NotNull UUID eventId,
        @NotNull @PositiveOrZero Integer totalQty) {
}
