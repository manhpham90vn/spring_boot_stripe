package com.manhpham.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gọi API NỘI BỘ của Inventory ({@code /internal/**}) qua DNS. Ba thao tác saga:
 * hold (giữ chỗ) → commit (chốt SOLD) → release (bù trừ).
 */
@Component
public class InventoryClient {

    private final RestClient http;

    public InventoryClient(RestClient.Builder builder, @Value("${services.inventory.url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public HoldResult hold(UUID ticketTypeId, int quantity, UUID orderId) {
        return http.post()
                .uri("/internal/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ticketTypeId", ticketTypeId, "quantity", quantity, "orderId", orderId))
                .retrieve()
                .body(HoldResult.class);
    }

    /** SEATED: giữ các ghế cụ thể (SET NX từng ghế ở Inventory). */
    public HoldResult holdSeats(UUID ticketTypeId, List<UUID> seatIds, UUID orderId) {
        return http.post()
                .uri("/internal/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ticketTypeId", ticketTypeId, "seatIds", seatIds, "orderId", orderId))
                .retrieve()
                .body(HoldResult.class);
    }

    public void commit(UUID holdId) {
        http.post().uri("/internal/holds/{id}/commit", holdId).retrieve().toBodilessEntity();
    }

    public void release(UUID holdId) {
        http.delete().uri("/internal/holds/{id}", holdId).retrieve().toBodilessEntity();
    }

    public record HoldResult(UUID holdId, UUID ticketTypeId, int quantity) {
    }
}
