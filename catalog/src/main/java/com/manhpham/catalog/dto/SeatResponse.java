package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.SeatMap;

import java.io.Serializable;
import java.util.UUID;

/**
 * Một ghế trong seat-map (cho FE chọn ghế). Seat-map (tĩnh) thuộc Catalog; {@code status}
 * (AVAILABLE/HELD/SOLD) gộp từ Inventory — null khi tra cứu lẻ không cần trạng thái.
 */
public record SeatResponse(
        UUID seatId,
        String section,
        String rowLabel,
        String seatNumber,
        String label,
        String status) implements Serializable {

    /** Không kèm trạng thái (dùng cho tra cứu ghế lẻ /internal). */
    public static SeatResponse from(SeatMap s) {
        return from(s, null);
    }

    public static SeatResponse from(SeatMap s, String status) {
        return new SeatResponse(s.getId(), s.getSection(), s.getRowLabel(), s.getSeatNumber(), s.label(), status);
    }
}
