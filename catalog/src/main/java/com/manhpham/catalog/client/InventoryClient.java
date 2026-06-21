package com.manhpham.catalog.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gọi API NỘI BỘ của Inventory ({@code /internal/**}) qua DNS để seed tồn kho khi admin
 * tạo/sửa loại vé. Catalog giữ GIÁ, Inventory giữ SỐ LƯỢNG (bất biến kiến trúc) — nên
 * "tổng số vé" không lưu ở Catalog mà được chuyển thẳng sang Inventory.
 */
@Component
@Slf4j
public class InventoryClient {

    private final RestClient http;

    // Tự dựng RestClient (không inject RestClient.Builder): Catalog không có spring-cloud
    // nên Boot không tạo sẵn bean Builder như ở Order service.
    public InventoryClient(@Value("${services.inventory.url}") String baseUrl) {
        this.http = RestClient.create(baseUrl);
    }

    /**
     * Seed/đặt lại tồn cho một loại vé. PUT idempotent — gọi lại với cùng totalQty an toàn.
     * Lỗi (Inventory chết / 4xx) sẽ ném ra để rollback transaction tạo loại vé ở Catalog.
     */
    public void seedStock(UUID ticketTypeId, UUID eventId, int totalQty) {
        http.put()
                .uri("/internal/stock/{ticketTypeId}", ticketTypeId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("eventId", eventId, "totalQty", totalQty))
                .retrieve()
                .toBodilessEntity();
    }

    /** Seed tồn SEATED: gửi danh sách seatId (= seat_map.id) để Inventory dựng seat_inventory. */
    public void seedSeats(UUID ticketTypeId, UUID eventId, List<UUID> seatIds) {
        http.put()
                .uri("/internal/stock/{ticketTypeId}", ticketTypeId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("eventId", eventId, "seatIds", seatIds))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Trạng thái từng ghế (AVAILABLE/HELD/SOLD) → {@code seatId -> status}, để gộp vào seat-map.
     * Inventory chết / lỗi: nuốt lỗi, trả map rỗng (FE coi như AVAILABLE) — không chặn việc duyệt.
     */
    public Map<UUID, String> seatStatuses(UUID ticketTypeId) {
        try {
            SeatStatus[] arr = http.get()
                    .uri("/internal/stock/{ticketTypeId}/seats", ticketTypeId)
                    .retrieve()
                    .body(SeatStatus[].class);
            if (arr == null) {
                return Map.of();
            }
            return Arrays.stream(arr).collect(Collectors.toMap(SeatStatus::seatId, SeatStatus::status));
        } catch (RestClientException e) {
            log.warn("Không lấy được trạng thái ghế ticketType={}: {}", ticketTypeId, e.getMessage());
            return Map.of();
        }
    }

    /** Bản đồ trạng thái ghế trả về từ Inventory. */
    private record SeatStatus(UUID seatId, String status) {
    }
}
