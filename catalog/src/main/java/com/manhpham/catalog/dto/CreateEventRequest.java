package com.manhpham.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Dữ liệu tạo sự kiện (admin). {@code @Valid} ở controller bắt buộc các field gắn ràng
 * buộc phải hợp lệ trước khi vào service. Các field không gắn annotation (description,
 * salesStartAt, salesEndAt) là TÙY CHỌN — được phép null.
 */
public record CreateEventRequest(
        @NotNull UUID venueId,                       // bắt buộc trỏ tới một venue đã có
        @NotBlank @Size(max = 300) String title,     // tiêu đề bắt buộc, tối đa 300 ký tự
        String description,                          // mô tả: tùy chọn
        @NotNull Instant startsAt,                   // giờ diễn: bắt buộc
        Instant salesStartAt,                        // mốc mở bán: tùy chọn
        Instant salesEndAt) {                        // mốc đóng bán: tùy chọn
}
