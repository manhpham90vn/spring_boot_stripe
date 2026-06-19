package com.manhpham.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVenueRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 120) String city) {
}
