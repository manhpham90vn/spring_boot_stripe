package com.manhpham.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/** Lệnh thu tiền cho một đơn (từ Order/saga). amount theo minor units, currency ISO-4217. */
public record ChargeRequest(
        @NotNull UUID orderId,
        @NotNull @PositiveOrZero Long amountMinor,
        @Pattern(regexp = "[A-Z]{3}", message = "currency phải là mã ISO-4217 3 ký tự in hoa") String currency) {
}
