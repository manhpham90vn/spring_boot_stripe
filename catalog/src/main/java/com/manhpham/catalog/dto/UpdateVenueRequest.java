package com.manhpham.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu cập nhật địa điểm (admin). Cùng ràng buộc với CreateVenueRequest. */
public record UpdateVenueRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 120) String city) {
}
