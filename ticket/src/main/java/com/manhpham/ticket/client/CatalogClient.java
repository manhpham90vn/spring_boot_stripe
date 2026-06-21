package com.manhpham.ticket.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Gọi API NỘI BỘ của Catalog ({@code /internal/seats/{seatId}}) qua DNS để resolve NHÃN ghế
 * (in lên vé) lúc phát vé SEATED. Catalog giữ seat_map (nguồn của nhãn). Gọi nội bộ, không JWT.
 */
@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(@Value("${services.catalog.url}") String baseUrl) {
        this.http = RestClient.create(baseUrl);
    }

    /** Tra một ghế → nhãn ("A-12"). field thừa trong JSON được bỏ qua. */
    public SeatInfo getSeat(UUID seatId) {
        return http.get().uri("/internal/seats/{id}", seatId).retrieve().body(SeatInfo.class);
    }

    public record SeatInfo(UUID seatId, String section, String rowLabel, String seatNumber, String label) {
    }
}
