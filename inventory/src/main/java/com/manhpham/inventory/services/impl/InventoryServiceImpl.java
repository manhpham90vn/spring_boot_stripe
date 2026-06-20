package com.manhpham.inventory.services.impl;

import com.manhpham.inventory.dto.HoldRequest;
import com.manhpham.inventory.dto.HoldResponse;
import com.manhpham.inventory.entities.TicketStock;
import com.manhpham.inventory.repositories.jpa.TicketStockRepository;
import com.manhpham.inventory.services.InventoryService;
import com.manhpham.inventory.utils.exception.HoldNotFoundException;
import com.manhpham.inventory.utils.exception.InsufficientStockException;
import com.manhpham.inventory.utils.exception.StockNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Hiện thực tồn kho GA. Điểm cốt lõi: dùng **thao tác nguyên tử của Redis** (DECRBY/INCRBY)
 * cho điểm tranh chấp — hàng nghìn request song song KHÔNG thể cùng lấy quá số tồn (chống
 * oversell). PostgreSQL chỉ ghi trạng thái cuối SOLD (nguồn sự thật bền).
 */
@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final TicketStockRepository stocks;
    private final StringRedisTemplate redis;
    private final Duration holdTtl;

    public InventoryServiceImpl(TicketStockRepository stocks, StringRedisTemplate redis,
                                @Value("${inventory.hold.ttl:PT15M}") Duration holdTtl) {
        this.stocks = stocks;
        this.redis = redis;
        this.holdTtl = holdTtl;
    }

    // --- Redis key helpers ---------------------------------------------------
    private static String gaKey(UUID ticketTypeId) { return "inv:ga:" + ticketTypeId; }
    private static String holdKey(UUID holdId) { return "inv:hold:" + holdId; }
    private static String orderKey(UUID orderId) { return "inv:order:" + orderId; }

    @Override
    @Transactional
    public void seed(UUID ticketTypeId, UUID eventId, int totalQty) {
        TicketStock stock = stocks.findById(ticketTypeId).orElse(null);
        if (stock == null) {
            stock = stocks.save(TicketStock.create(ticketTypeId, eventId, totalQty));
        } else {
            stock.resetTotal(totalQty); // dirty checking persist
        }
        // Nạp counter Redis = số còn lại (= total - sold).
        redis.opsForValue().set(gaKey(ticketTypeId), String.valueOf(stock.available()));
        log.info("Seeded stock ticketType={} total={} available={}", ticketTypeId, totalQty, stock.available());
    }

    @Override
    public int available(UUID ticketTypeId) {
        String v = redis.opsForValue().get(gaKey(ticketTypeId));
        if (v == null) {
            throw new StockNotFoundException(ticketTypeId);
        }
        return Integer.parseInt(v);
    }

    @Override
    public HoldResponse hold(HoldRequest request) {
        UUID ticketTypeId = request.ticketTypeId();
        int qty = request.quantity();

        // Idempotency theo orderId: nếu đã giữ chỗ cho đơn này và hold còn sống → trả lại.
        String existingHold = redis.opsForValue().get(orderKey(request.orderId()));
        if (existingHold != null) {
            String v = redis.opsForValue().get(holdKey(UUID.fromString(existingHold)));
            if (v != null) {
                Parsed p = Parsed.of(v);
                return new HoldResponse(UUID.fromString(existingHold), p.ticketTypeId, p.qty);
            }
        }

        if (redis.opsForValue().get(gaKey(ticketTypeId)) == null) {
            throw new StockNotFoundException(ticketTypeId); // chưa seed
        }

        // DECRBY nguyên tử: nếu kết quả < 0 nghĩa là không đủ → trả lại ngay.
        Long left = redis.opsForValue().decrement(gaKey(ticketTypeId), qty);
        if (left == null || left < 0) {
            redis.opsForValue().increment(gaKey(ticketTypeId), qty);
            throw new InsufficientStockException(ticketTypeId);
        }

        UUID holdId = UUID.randomUUID();
        redis.opsForValue().set(holdKey(holdId), ticketTypeId + "|" + qty, holdTtl);
        redis.opsForValue().set(orderKey(request.orderId()), holdId.toString(), holdTtl);
        log.info("Hold {} qty={} ticketType={} left={}", holdId, qty, ticketTypeId, left);
        return new HoldResponse(holdId, ticketTypeId, qty);
    }

    @Override
    @Transactional
    public void commit(UUID holdId) {
        String v = redis.opsForValue().get(holdKey(holdId));
        if (v == null) {
            throw new HoldNotFoundException(holdId); // đã nhả/hết hạn/không tồn tại
        }
        Parsed p = Parsed.of(v);
        TicketStock stock = stocks.findById(p.ticketTypeId)
                .orElseThrow(() -> new StockNotFoundException(p.ticketTypeId));
        stock.commitSold(p.qty); // ghi bền SOLD (counter Redis đã trừ từ lúc HOLD)
        redis.delete(holdKey(holdId)); // chốt rồi → xoá, chống release nhầm
        log.info("Commit hold {} ticketType={} qty={} sold={}", holdId, p.ticketTypeId, p.qty, stock.getSoldQty());
    }

    @Override
    public void release(UUID holdId) {
        String v = redis.opsForValue().get(holdKey(holdId));
        if (v == null) {
            return; // idempotent: đã nhả hoặc đã commit
        }
        Parsed p = Parsed.of(v);
        redis.opsForValue().increment(gaKey(p.ticketTypeId), p.qty); // trả lại available
        redis.delete(holdKey(holdId));
        log.info("Release hold {} ticketType={} qty={}", holdId, p.ticketTypeId, p.qty);
    }

    /** Giá trị hold lưu trong Redis dạng "ticketTypeId|qty". */
    private record Parsed(UUID ticketTypeId, int qty) {
        static Parsed of(String v) {
            String[] parts = v.split("\\|");
            return new Parsed(UUID.fromString(parts[0]), Integer.parseInt(parts[1]));
        }
    }
}
