package com.manhpham.waitingroom.client;

import com.manhpham.waitingroom.config.WaitingRoomProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Đọc tồn kho còn lại từ Inventory ({@code GET /internal/stock/{ticketTypeId}}) để admission biết
 * khi nào NGỪNG thả (không đẩy người vào tranh con số 0). Best-effort: lỗi/timeout coi như "còn"
 * để không vô cớ chặn hàng — cờ sold-out (Order/Inventory bắn sang) mới là tín hiệu dừng dứt khoát.
 *
 * <p>Inventory đếm theo {@code ticketTypeId}; waitingroom gác theo {@code eventId}. Cầu nối là
 * {@code waitingroom.event-ticket-types} (xem {@link WaitingRoomProperties}). Không cấu hình mapping
 * → trả "còn" (true), dựa hoàn toàn vào cờ sold-out + rate.
 */
@Component
@Slf4j
public class InventoryClient {

    private final WebClient client;
    private final WaitingRoomProperties props;

    public InventoryClient(WebClient inventoryWebClient, WaitingRoomProperties props) {
        this.client = inventoryWebClient;
        this.props = props;
    }

    /** Còn ít nhất 1 vé cho event? Tổng available của các ticketType đã map. */
    public Mono<Boolean> hasStock(UUID eventId) {
        List<UUID> ticketTypes = props.eventTicketTypes().get(eventId);
        if (ticketTypes == null || ticketTypes.isEmpty()) {
            return Mono.just(true); // không map → để cờ sold-out quyết định
        }
        return reactor.core.publisher.Flux.fromIterable(ticketTypes)
                .flatMap(this::available)
                .reduce(0, Integer::sum)
                .map(total -> total > 0)
                .onErrorResume(ex -> {
                    log.warn("Hỏi tồn kho event {} lỗi ({}), tạm coi là CÒN", eventId, ex.toString());
                    return Mono.just(true);
                });
    }

    private Mono<Integer> available(UUID ticketTypeId) {
        return client.get()
                .uri("/internal/stock/{id}", ticketTypeId)
                .retrieve()
                .bodyToMono(Availability.class)
                .map(Availability::available)
                .defaultIfEmpty(0);
    }

    /** Khớp {@code AvailabilityResponse} của Inventory (chỉ cần trường available). */
    private record Availability(UUID ticketTypeId, int available) {
    }
}
