package com.manhpham.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Yêu cầu đặt mua từ client. Giá KHÔNG do client gửi — Order tự hỏi Catalog. */
public record PlaceOrderRequest(
        @NotNull UUID eventId,
        @NotNull UUID ticketTypeId,
        @Min(1) int quantity) {
}
