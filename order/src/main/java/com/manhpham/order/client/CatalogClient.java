package com.manhpham.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Gọi Catalog lấy GIÁ loại vé — giá luôn tính ở server, KHÔNG tin client (payment_issue.md 4.1).
 * Dùng endpoint công khai của Catalog (không cần token). Gọi qua DNS nội bộ.
 */
@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(RestClient.Builder builder, @Value("${services.catalog.url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public TicketTypeInfo getTicketType(UUID eventId, UUID ticketTypeId) {
        return http.get()
                .uri("/api/catalog/public/events/{e}/ticket-types/{t}", eventId, ticketTypeId)
                .retrieve()
                .body(TicketTypeInfo.class);
    }

    /** Lấy đúng field cần (giá + tiền tệ + trần mỗi đơn); field thừa trong JSON được bỏ qua. */
    public record TicketTypeInfo(UUID id, String name, long priceMinor, String currency, int maxPerOrder) {
    }
}
