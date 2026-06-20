package com.manhpham.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Dữ liệu cập nhật sự kiện (admin). Trùng cấu trúc với CreateEventRequest nhưng tách
 * thành record riêng để mỗi thao tác có "hợp đồng" rõ ràng — sau này nếu tạo và sửa cần
 * ràng buộc khác nhau thì không vướng nhau. Service chỉ cho cập nhật khi sự kiện còn DRAFT.
 */
public record UpdateEventRequest(
        @NotNull UUID venueId,
        @NotBlank @Size(max = 300) String title,
        String description,
        @NotNull Instant startsAt,
        Instant salesStartAt,
        Instant salesEndAt) {
}
