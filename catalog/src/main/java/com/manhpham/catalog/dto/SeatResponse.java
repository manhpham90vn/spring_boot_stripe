package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.SeatMap;

import java.io.Serializable;
import java.util.UUID;

/** Một ghế trong seat-map (cho FE chọn ghế). Trạng thái bán/free thuộc Inventory. */
public record SeatResponse(
        UUID seatId,
        String section,
        String rowLabel,
        String seatNumber,
        String label) implements Serializable {

    public static SeatResponse from(SeatMap s) {
        return new SeatResponse(s.getId(), s.getSection(), s.getRowLabel(), s.getSeatNumber(), s.label());
    }
}
