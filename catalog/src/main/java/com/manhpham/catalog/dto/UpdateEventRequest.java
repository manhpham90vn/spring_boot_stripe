package com.manhpham.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record UpdateEventRequest(
        @NotNull UUID venueId,
        @NotBlank @Size(max = 300) String title,
        String description,
        @NotNull Instant startsAt,
        Instant salesStartAt,
        Instant salesEndAt) {
}
