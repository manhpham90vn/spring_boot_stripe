package com.manhpham.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Lệnh hủy PaymentIntent của một đơn (từ Order/saga khi bỏ cuộc — saga-purchase-flow.md §4). */
public record CancelRequest(@NotNull UUID orderId) {
}
