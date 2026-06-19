package com.manhpham.catalog.controller;

import com.manhpham.catalog.dto.EventDetailResponse;
import com.manhpham.catalog.dto.EventSummaryResponse;
import com.manhpham.catalog.dto.TicketTypeResponse;
import com.manhpham.catalog.dto.VenueResponse;
import com.manhpham.catalog.services.EventService;
import com.manhpham.catalog.services.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Duyệt danh mục — công khai (không cần JWT). Phục vụ dưới {@code /api/catalog/**}
 * (GET), gateway route thẳng vào đây. Đọc nhiều ghi ít → kết quả được cache Redis
 * ở tầng service.
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final EventService eventService;
    private final VenueService venueService;

    @GetMapping("/events")
    public List<EventSummaryResponse> listEvents() {
        return eventService.listPublished();
    }

    @GetMapping("/events/{id}")
    public EventDetailResponse eventDetail(@PathVariable UUID id) {
        return eventService.getDetail(id);
    }

    @GetMapping("/events/{eventId}/ticket-types")
    public List<TicketTypeResponse> listTicketTypes(@PathVariable UUID eventId) {
        return eventService.listTicketTypes(eventId);
    }

    @GetMapping("/events/{eventId}/ticket-types/{ticketTypeId}")
    public TicketTypeResponse ticketTypeDetail(@PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId) {
        return eventService.getTicketType(eventId, ticketTypeId);
    }

    @GetMapping("/venues")
    public List<VenueResponse> listVenues() {
        return venueService.listAll();
    }

    @GetMapping("/venues/{id}")
    public VenueResponse venueDetail(@PathVariable UUID id) {
        return venueService.get(id);
    }
}
