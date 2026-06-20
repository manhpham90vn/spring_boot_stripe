package com.manhpham.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Yêu cầu GIỮ CHỖ từ Order (saga). {@code orderId} dùng làm khoá idempotency: gọi lại cùng
 * orderId trả về CÙNG hold, không trừ tồn lần hai (payment_issue.md 2.x).
 */
public record HoldRequest(
        @NotNull UUID ticketTypeId,
        @Min(1) int quantity,
        @NotNull UUID orderId) {
}
