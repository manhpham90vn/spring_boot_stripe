package com.manhpham.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateTicketTypeRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        // Đơn vị nhỏ nhất (minor units). Với JPY = chính số tiền (zero-decimal).
        @NotNull @PositiveOrZero Long priceMinor,
        // ISO-4217, vd "USD", "JPY", "VND".
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "currency phải là mã ISO-4217 3 ký tự in hoa") String currency,
        @Min(1) int maxPerOrder,
        // Tổng số vé phát hành — đặt lại tồn ở Inventory (chỉ cho sửa khi sự kiện còn DRAFT).
        @NotNull @PositiveOrZero Integer totalQty) {
}
