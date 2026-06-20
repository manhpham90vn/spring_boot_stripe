package com.manhpham.catalog.services.impl;

import com.manhpham.catalog.dto.CreateEventRequest;
import com.manhpham.catalog.dto.CreateTicketTypeRequest;
import com.manhpham.catalog.dto.EventDetailResponse;
import com.manhpham.catalog.dto.EventSummaryResponse;
import com.manhpham.catalog.dto.TicketTypeResponse;
import com.manhpham.catalog.dto.UpdateEventRequest;
import com.manhpham.catalog.dto.UpdateTicketTypeRequest;
import com.manhpham.catalog.dto.VenueResponse;
import com.manhpham.catalog.entities.Event;
import com.manhpham.catalog.entities.EventStatus;
import com.manhpham.catalog.entities.TicketType;
import com.manhpham.catalog.entities.Venue;
import com.manhpham.catalog.repositories.jpa.EventRepository;
import com.manhpham.catalog.repositories.jpa.TicketTypeRepository;
import com.manhpham.catalog.repositories.jpa.VenueRepository;
import com.manhpham.catalog.services.EventService;
import com.manhpham.catalog.utils.exception.CatalogConflictException;
import com.manhpham.catalog.utils.exception.EventNotFoundException;
import com.manhpham.catalog.utils.exception.TicketTypeNotFoundException;
import com.manhpham.catalog.utils.exception.VenueNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ Catalog cho Event + TicketType. Đây là service "đọc nhiều, ghi ít" nên có
 * hai điểm đáng học:
 *
 * <ul>
 *   <li><b>Cache (Redis)</b>: các hàm ĐỌC gắn {@code @Cacheable} để lần sau lấy thẳng từ
 *       cache; các hàm GHI gắn {@code @CacheEvict}/{@code @Caching} để xóa cache cũ, tránh
 *       trả dữ liệu lỗi thời. Quy ước cache name: {@code "events"} (các danh sách),
 *       {@code "event"} (chi tiết theo id), {@code "venues"} (danh sách venue).</li>
 *   <li><b>{@code @Transactional}</b>: hàm chỉ đọc đặt {@code readOnly = true} để DB tối
 *       ưu (không cần theo dõi thay đổi); hàm ghi để mặc định (đọc-ghi). Trong cùng một
 *       transaction, sửa entity đã nạp sẽ tự được flush khi commit — KHÔNG cần gọi save()
 *       lại (xem changeStatus/update bên dưới: "dirty checking" của JPA).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final VenueRepository venues;

    // @Cacheable("events"): kết quả được lưu vào cache "events"; lần gọi sau (cùng tham số)
    // trả thẳng từ cache, không chạm DB. Đây là danh sách công khai → bỏ qua DRAFT.
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "events")
    public List<EventSummaryResponse> listPublished() {
        List<Event> published = events.findByStatusNotOrderByStartsAtAsc(EventStatus.DRAFT);
        // Nạp Venue một lần cho cả danh sách (tránh N+1).
        Map<UUID, Venue> venueById = venues.findAllById(
                        published.stream().map(Event::getVenueId).distinct().toList()).stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        return published.stream()
                .map(e -> EventSummaryResponse.of(e, venueResponse(venueById.get(e.getVenueId()))))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listAll() {
        List<Event> all = events.findAllByOrderByStartsAtAsc();
        Map<UUID, Venue> venueById = venues.findAllById(
                        all.stream().map(Event::getVenueId).distinct().toList()).stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        return all.stream()
                .map(e -> EventSummaryResponse.of(e, venueResponse(venueById.get(e.getVenueId()))))
                .toList();
    }

    // key = "#eventId": mỗi sự kiện một mục cache riêng (cache "event" theo id), thay vì
    // gom chung như danh sách. SpEL "#eventId" tham chiếu tham số cùng tên.
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "event", key = "#eventId")
    public EventDetailResponse getDetail(UUID eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        return buildDetail(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketTypeResponse> listTicketTypes(UUID eventId) {
        if (!events.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        return ticketTypes.findByEventIdOrderByPriceMinorAsc(eventId).stream()
                .map(TicketTypeResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketTypeResponse getTicketType(UUID eventId, UUID ticketTypeId) {
        return TicketTypeResponse.from(loadTicketTypeOfEvent(eventId, ticketTypeId));
    }

    // @CacheEvict(allEntries = true): tạo sự kiện mới làm MỌI danh sách "events" cũ sai →
    // xóa sạch cache đó để lần đọc kế tiếp dựng lại từ DB. allEntries vì ta không biết mục
    // danh sách nào chứa nó (danh sách được cache theo nhiều biến thể).
    @Override
    @Transactional
    @CacheEvict(cacheNames = "events", allEntries = true)
    public EventDetailResponse create(CreateEventRequest request) {
        // Kiểm tra venue tồn tại TRƯỚC khi tạo event — tránh tạo event trỏ tới venue ma.
        if (!venues.existsById(request.venueId())) {
            throw new VenueNotFoundException(request.venueId());
        }
        Event saved = events.save(Event.create(request.venueId(), request.title(), request.description(),
                request.startsAt(), request.salesStartAt(), request.salesEndAt()));
        return buildDetail(saved);
    }

    // @Caching gộp NHIỀU evict: vừa xóa danh sách ("events", allEntries) vừa xóa đúng mục
    // chi tiết của sự kiện này ("event" theo key) — vì đổi trạng thái ảnh hưởng cả hai.
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "events", allEntries = true),
            @CacheEvict(cacheNames = "event", key = "#eventId")
    })
    public EventDetailResponse changeStatus(UUID eventId, EventStatus status) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        // Sửa entity trong transaction là đủ: JPA "dirty checking" sẽ tự UPDATE khi commit,
        // không cần gọi events.save(event) — đây là điểm hay khiến nhiều người mới bối rối.
        event.changeStatus(status);
        return buildDetail(event);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "event", key = "#eventId")
    public TicketTypeResponse addTicketType(UUID eventId, CreateTicketTypeRequest request) {
        if (!events.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        TicketType saved = ticketTypes.save(TicketType.create(eventId, request.name(), request.description(),
                request.priceMinor(), request.currency(), request.maxPerOrder()));
        return TicketTypeResponse.from(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "events", allEntries = true),
            @CacheEvict(cacheNames = "event", key = "#eventId")
    })
    public EventDetailResponse update(UUID eventId, UpdateEventRequest request) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        requireDraft(event, "sửa");
        if (!venues.existsById(request.venueId())) {
            throw new VenueNotFoundException(request.venueId());
        }
        event.update(request.venueId(), request.title(), request.description(),
                request.startsAt(), request.salesStartAt(), request.salesEndAt());
        return buildDetail(event);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "events", allEntries = true),
            @CacheEvict(cacheNames = "event", key = "#eventId")
    })
    public void delete(UUID eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.getStatus() == EventStatus.DRAFT) {
            // Chưa phát hành → chưa thể có tồn kho/đơn → xóa hẳn (kèm loại vé con).
            ticketTypes.deleteByEventId(eventId);
            events.delete(event);
        } else {
            // Đã phát hành → Inventory/Order có thể đã tham chiếu → soft-delete.
            event.changeStatus(EventStatus.CANCELLED);
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "event", key = "#eventId")
    public TicketTypeResponse updateTicketType(UUID eventId, UUID ticketTypeId,
            UpdateTicketTypeRequest request) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        requireDraft(event, "sửa loại vé của");
        TicketType ticketType = loadTicketTypeOfEvent(eventId, ticketTypeId);
        ticketType.update(request.name(), request.description(), request.priceMinor(),
                request.currency(), request.maxPerOrder());
        return TicketTypeResponse.from(ticketType);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "event", key = "#eventId")
    public void deleteTicketType(UUID eventId, UUID ticketTypeId) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        requireDraft(event, "xóa loại vé của");
        TicketType ticketType = loadTicketTypeOfEvent(eventId, ticketTypeId);
        ticketTypes.delete(ticketType);
    }

    // ---- helpers -----------------------------------------------------------

    /** Loại vé phải tồn tại VÀ thuộc đúng sự kiện (tránh sửa nhầm xuyên event). */
    private TicketType loadTicketTypeOfEvent(UUID eventId, UUID ticketTypeId) {
        TicketType ticketType = ticketTypes.findById(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(ticketTypeId));
        if (!ticketType.getEventId().equals(eventId)) {
            throw new TicketTypeNotFoundException(ticketTypeId);
        }
        return ticketType;
    }

    /** Guard: chỉ cho ghi khi sự kiện còn DRAFT (đã phát hành thì chặn 409). */
    private static void requireDraft(Event event, String action) {
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new CatalogConflictException(
                    "Chỉ được " + action + " sự kiện khi còn DRAFT; trạng thái hiện tại: "
                            + event.getStatus());
        }
    }

    private EventDetailResponse buildDetail(Event event) {
        Venue venue = venues.findById(event.getVenueId())
                .orElseThrow(() -> new VenueNotFoundException(event.getVenueId()));
        List<TicketTypeResponse> types = ticketTypes.findByEventIdOrderByPriceMinorAsc(event.getId()).stream()
                .map(TicketTypeResponse::from).toList();
        return EventDetailResponse.of(event, venueResponse(venue), types);
    }

    private static VenueResponse venueResponse(Venue venue) {
        return venue == null ? null : VenueResponse.from(venue);
    }
}
