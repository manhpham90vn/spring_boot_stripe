package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.Event;
import com.manhpham.catalog.entities.EventStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Dòng tóm tắt cho trang danh sách (không kèm ticket type/mô tả dài). */
public record EventSummaryResponse(
        UUID id,
        String title,
        EventStatus status,
        Instant startsAt,
        VenueResponse venue) implements Serializable {

    public static EventSummaryResponse of(Event e, VenueResponse venue) {
        return new EventSummaryResponse(e.getId(), e.getTitle(), e.getStatus(), e.getStartsAt(), venue);
    }
}
