package com.manhpham.ticket.dto;

import jakarta.validation.constraints.NotBlank;

/** Cổng gửi token quét được từ QR để kiểm tra. */
public record ValidateRequest(@NotBlank String qrToken) {
}
