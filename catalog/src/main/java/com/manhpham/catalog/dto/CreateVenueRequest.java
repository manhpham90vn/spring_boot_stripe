package com.manhpham.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu tạo địa điểm (admin). Chỉ {@code name} bắt buộc; address/city tùy chọn. */
public record CreateVenueRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 120) String city) {
}
