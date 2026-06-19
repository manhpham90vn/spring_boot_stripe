package com.manhpham.catalog.dto;

import com.manhpham.catalog.entities.Event;
import com.manhpham.catalog.entities.EventStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chi tiết sự kiện: kèm địa điểm + các loại vé (dùng cho trang chi tiết, cache). */
public record EventDetailResponse(
        UUID id,
        String title,
        String description,
        EventStatus status,
        Instant startsAt,
        Instant salesStartAt,
        Instant salesEndAt,
        VenueResponse venue,
        List<TicketTypeResponse> ticketTypes) implements Serializable {

    public static EventDetailResponse of(Event e, VenueResponse venue, List<TicketTypeResponse> ticketTypes) {
        return new EventDetailResponse(e.getId(), e.getTitle(), e.getDescription(), e.getStatus(),
                e.getStartsAt(), e.getSalesStartAt(), e.getSalesEndAt(), venue, ticketTypes);
    }
}
