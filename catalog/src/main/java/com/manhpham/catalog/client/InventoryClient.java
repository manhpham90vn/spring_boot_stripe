package com.manhpham.catalog.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Gọi API NỘI BỘ của Inventory ({@code /internal/**}) qua DNS để seed tồn kho khi admin
 * tạo/sửa loại vé. Catalog giữ GIÁ, Inventory giữ SỐ LƯỢNG (bất biến kiến trúc) — nên
 * "tổng số vé" không lưu ở Catalog mà được chuyển thẳng sang Inventory.
 */
@Component
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
}
