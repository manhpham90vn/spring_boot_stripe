package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.EventStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeEventStatusRequest(@NotNull EventStatus status) {
}
