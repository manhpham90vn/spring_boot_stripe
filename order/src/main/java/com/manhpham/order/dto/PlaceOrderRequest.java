package com.manhpham.order.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu đặt mua từ client. Giá KHÔNG do client gửi — Order tự hỏi Catalog.
 * GA: gửi {@code quantity} (≥1). SEATED: gửi {@code seatIds} (chọn ghế). Cross-field validate ở service.
 */
public record PlaceOrderRequest(
        @NotNull UUID eventId,
        @NotNull UUID ticketTypeId,
        Integer quantity,
        List<UUID> seatIds) {

    public boolean seated() {
        return seatIds != null && !seatIds.isEmpty();
    }

    /** Số vé: SEATED = số ghế; GA = quantity. */
    public int count() {
        return seated() ? seatIds.size() : (quantity == null ? 0 : quantity);
    }
}
