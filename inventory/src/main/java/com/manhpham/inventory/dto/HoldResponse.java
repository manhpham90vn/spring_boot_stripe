package com.manhpham.inventory.dto;

import java.util.UUID;

/** Kết quả giữ chỗ: holdId để sau đó commit/release. */
public record HoldResponse(UUID holdId, UUID ticketTypeId, int quantity) {
}
