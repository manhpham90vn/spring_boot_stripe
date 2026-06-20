package com.manhpham.inventory.dto;

import java.util.UUID;

/** Số vé GA còn lại (đọc từ counter Redis). */
public record AvailabilityResponse(UUID ticketTypeId, int available) {
}
