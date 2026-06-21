package com.manhpham.catalog.services;

import com.manhpham.catalog.dto.CreateEventRequest;
import com.manhpham.catalog.dto.CreateTicketTypeRequest;
import com.manhpham.catalog.dto.EventDetailResponse;
import com.manhpham.catalog.dto.EventSummaryResponse;
import com.manhpham.catalog.dto.SeatResponse;
import com.manhpham.catalog.dto.TicketTypeResponse;
import com.manhpham.catalog.dto.UpdateEventRequest;
import com.manhpham.catalog.dto.UpdateTicketTypeRequest;
import com.manhpham.catalog.entities.EventStatus;

import java.util.List;
import java.util.UUID;

public interface EventService {

    // ---- Đọc (công khai, cache) -------------------------------------------
    /** Danh sách sự kiện đã phát hành (bỏ DRAFT), sắp theo giờ diễn. */
    List<EventSummaryResponse> listPublished();

    /** Danh sách admin: tất cả sự kiện gồm DRAFT (không cache). */
    List<EventSummaryResponse> listAll();

    /** Chi tiết một sự kiện kèm địa điểm + loại vé. */
    EventDetailResponse getDetail(UUID eventId);

    /** Các loại vé của một sự kiện. */
    List<TicketTypeResponse> listTicketTypes(UUID eventId);

    /** Một loại vé cụ thể (phải thuộc {@code eventId}). */
    TicketTypeResponse getTicketType(UUID eventId, UUID ticketTypeId);

    /** Bản đồ ghế của một loại vé SEATED (cho FE chọn ghế). 400 nếu loại vé là GA. */
    List<SeatResponse> listSeats(UUID eventId, UUID ticketTypeId);

    /** Tra một ghế theo seatId (internal — Ticket resolve label lúc phát vé). 404 nếu không có. */
    SeatResponse getSeat(UUID seatId);

    // ---- Ghi (admin) -------------------------------------------------------
    EventDetailResponse create(CreateEventRequest request);

    EventDetailResponse changeStatus(UUID eventId, EventStatus status);

    /** Sửa sự kiện; chỉ cho phép khi còn DRAFT (else {@code CatalogConflictException}). */
    EventDetailResponse update(UUID eventId, UpdateEventRequest request);

    /** Xóa sự kiện: DRAFT → xóa hẳn (kèm loại vé); đã phát hành → soft-delete sang CANCELLED. */
    void delete(UUID eventId);

    TicketTypeResponse addTicketType(UUID eventId, CreateTicketTypeRequest request);

    /** Sửa loại vé; chỉ cho phép khi sự kiện còn DRAFT. */
    TicketTypeResponse updateTicketType(UUID eventId, UUID ticketTypeId, UpdateTicketTypeRequest request);

    /** Xóa loại vé; chỉ cho phép khi sự kiện còn DRAFT. */
    void deleteTicketType(UUID eventId, UUID ticketTypeId);
}
