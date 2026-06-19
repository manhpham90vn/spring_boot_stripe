package com.manhpham.catalog.controller;

import com.manhpham.catalog.config.OpenApiConfig;
import com.manhpham.catalog.dto.ChangeEventStatusRequest;
import com.manhpham.catalog.dto.CreateEventRequest;
import com.manhpham.catalog.dto.CreateTicketTypeRequest;
import com.manhpham.catalog.dto.CreateVenueRequest;
import com.manhpham.catalog.dto.EventDetailResponse;
import com.manhpham.catalog.dto.EventSummaryResponse;
import com.manhpham.catalog.dto.TicketTypeResponse;
import com.manhpham.catalog.dto.UpdateEventRequest;
import com.manhpham.catalog.dto.UpdateTicketTypeRequest;
import com.manhpham.catalog.dto.UpdateVenueRequest;
import com.manhpham.catalog.dto.VenueResponse;
import com.manhpham.catalog.services.EventService;
import com.manhpham.catalog.services.VenueService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Quản trị danh mục — cần JWT role ADMIN (xem {@code SecurityConfig}). Phục vụ
 * dưới {@code /api/catalog/admin/**}. Ghi vào đây sẽ evict cache đọc tương ứng.
 */
@RestController
@RequestMapping("/api/catalog/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class AdminCatalogController {

    private final EventService eventService;
    private final VenueService venueService;

    // ---- Venue -------------------------------------------------------------

    @PostMapping("/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@Valid @RequestBody CreateVenueRequest request) {
        return venueService.create(request);
    }

    @PutMapping("/venues/{id}")
    public VenueResponse updateVenue(@PathVariable UUID id,
            @Valid @RequestBody UpdateVenueRequest request) {
        return venueService.update(id, request);
    }

    @DeleteMapping("/venues/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVenue(@PathVariable UUID id) {
        venueService.delete(id);
    }

    // ---- Event -------------------------------------------------------------

    /** Danh sách admin: gồm cả DRAFT (khác endpoint công khai chỉ trả đã phát hành). */
    @GetMapping("/events")
    public List<EventSummaryResponse> listEvents() {
        return eventService.listAll();
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetailResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        return eventService.create(request);
    }

    @PutMapping("/events/{id}")
    public EventDetailResponse updateEvent(@PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest request) {
        return eventService.update(id, request);
    }

    @PutMapping("/events/{id}/status")
    public EventDetailResponse changeEventStatus(@PathVariable UUID id,
            @Valid @RequestBody ChangeEventStatusRequest request) {
        return eventService.changeStatus(id, request.status());
    }

    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable UUID id) {
        eventService.delete(id);
    }

    // ---- Ticket type -------------------------------------------------------

    @PostMapping("/events/{id}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse addTicketType(@PathVariable UUID id,
            @Valid @RequestBody CreateTicketTypeRequest request) {
        return eventService.addTicketType(id, request);
    }

    @PutMapping("/events/{id}/ticket-types/{ticketTypeId}")
    public TicketTypeResponse updateTicketType(@PathVariable UUID id,
            @PathVariable UUID ticketTypeId,
            @Valid @RequestBody UpdateTicketTypeRequest request) {
        return eventService.updateTicketType(id, ticketTypeId, request);
    }

    @DeleteMapping("/events/{id}/ticket-types/{ticketTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTicketType(@PathVariable UUID id, @PathVariable UUID ticketTypeId) {
        eventService.deleteTicketType(id, ticketTypeId);
    }
}
