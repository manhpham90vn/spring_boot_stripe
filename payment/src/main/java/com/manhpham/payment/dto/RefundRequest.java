package com.manhpham.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Lệnh hoàn tiền cho một đơn (từ Order/saga khi đã thu tiền nhưng hết vé — saga-purchase-flow.md §4). */
public record RefundRequest(@NotNull UUID orderId) {
}
