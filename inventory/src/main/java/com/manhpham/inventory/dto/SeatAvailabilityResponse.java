package com.manhpham.inventory.dto;

import java.util.UUID;

/**
 * Trạng thái bán của MỘT ghế (cho FE tô màu sơ đồ ghế). {@code status}:
 * {@code AVAILABLE} (chọn được) | {@code HELD} (đang có người giữ chỗ — Redis) |
 * {@code SOLD} (đã bán — Postgres). HELD/SOLD đều không cho chọn.
 */
public record SeatAvailabilityResponse(UUID seatId, String status) {
}
