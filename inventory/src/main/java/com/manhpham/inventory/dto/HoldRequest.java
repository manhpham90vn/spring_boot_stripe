package com.manhpham.inventory.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu GIỮ CHỖ từ Order (saga). {@code orderId} dùng làm khoá idempotency: gọi lại cùng
 * orderId trả về CÙNG hold, không trừ tồn lần hai (payment_issue.md 2.x).
 * GA: gửi {@code quantity}. SEATED: gửi {@code seatIds}. Cross-field validate ở service.
 */
public record HoldRequest(
        @NotNull UUID ticketTypeId,
        Integer quantity,
        List<UUID> seatIds,
        @NotNull UUID orderId) {

    public boolean seated() {
        return seatIds != null && !seatIds.isEmpty();
    }
}
