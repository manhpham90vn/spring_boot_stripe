package com.manhpham.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Một ghế khi tạo loại vé SEATED (đi vào seat_map). */
public record SeatInput(
        @NotBlank @Size(max = 64) String section,
        @NotBlank @Size(max = 16) String rowLabel,
        @NotBlank @Size(max = 16) String seatNumber) {
}
