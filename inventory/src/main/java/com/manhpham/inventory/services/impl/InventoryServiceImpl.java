package com.manhpham.inventory.services.impl;

import com.manhpham.inventory.dto.HoldRequest;
import com.manhpham.inventory.dto.HoldResponse;
import com.manhpham.inventory.dto.SeatAvailabilityResponse;
import com.manhpham.inventory.entities.SeatInventory;
import com.manhpham.inventory.entities.SeatStatus;
import com.manhpham.inventory.entities.TicketStock;
import com.manhpham.inventory.repositories.jpa.SeatInventoryRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hiện thực tồn kho GA + SEATED. Điểm cốt lõi: dùng **thao tác nguyên tử của Redis** cho điểm
 * tranh chấp — GA dùng DECRBY/INCRBY (counter), SEATED dùng SET NX từng ghế (giữ ghế tạm).
 * PostgreSQL ghi trạng thái cuối SOLD (nguồn sự thật bền): GA {@code sold_qty}, SEATED từng ghế.
 */
@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final TicketStockRepository stocks;
    private final SeatInventoryRepository seats;
    private final StringRedisTemplate redis;
    private final Duration holdTtl;

    public InventoryServiceImpl(TicketStockRepository stocks, SeatInventoryRepository seats,
                                StringRedisTemplate redis,
                                @Value("${inventory.hold.ttl:PT15M}") Duration holdTtl) {
        this.stocks = stocks;
        this.seats = seats;
        this.redis = redis;
        this.holdTtl = holdTtl;
    }

    // --- Redis key helpers ---------------------------------------------------
    private static String gaKey(UUID ticketTypeId) { return "inv:ga:" + ticketTypeId; }
    private static String holdKey(UUID holdId) { return "inv:hold:" + holdId; }
    private static String orderKey(UUID orderId) { return "inv:order:" + orderId; }
    private static String seatKey(UUID seatId) { return "inv:seat:" + seatId; }

    // ========================= SEED ==========================================

    @Override
    @Transactional
    public void seed(UUID ticketTypeId, UUID eventId, int totalQty) {
        TicketStock stock = stocks.findById(ticketTypeId).orElse(null);
        if (stock == null) {
            stock = stocks.save(TicketStock.create(ticketTypeId, eventId, totalQty));
        } else {
            stock.resetTotal(totalQty); // dirty checking persist
        }
        redis.opsForValue().set(gaKey(ticketTypeId), String.valueOf(stock.available()));
        log.info("Seeded GA stock ticketType={} total={} available={}", ticketTypeId, totalQty, stock.available());
    }

    @Override
    @Transactional
    public void seedSeats(UUID ticketTypeId, UUID eventId, List<UUID> seatIds) {
        for (UUID seatId : seatIds) {
            SeatInventory seat = seats.findById(seatId).orElse(null);
            if (seat == null) {
                seats.save(SeatInventory.create(seatId, ticketTypeId, eventId));
            } else {
                seat.resetAvailable(); // re-seed (DRAFT) → AVAILABLE
            }
        }
        log.info("Seeded SEATED stock ticketType={} seats={}", ticketTypeId, seatIds.size());
    }

    // ========================= AVAILABILITY ==================================

    @Override
    public int available(UUID ticketTypeId) {
        String v = redis.opsForValue().get(gaKey(ticketTypeId));
        if (v != null) {
            return Integer.parseInt(v); // GA
        }
        // SEATED: số ghế AVAILABLE ở Postgres (held-chưa-commit vẫn tính available — đủ cho gating).
        long total = seats.countByTicketTypeId(ticketTypeId);
        if (total == 0) {
            throw new StockNotFoundException(ticketTypeId); // chưa seed (cả GA lẫn SEATED)
        }
        return (int) seats.countByTicketTypeIdAndStatus(ticketTypeId, SeatStatus.AVAILABLE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatAvailabilityResponse> seatStatuses(UUID ticketTypeId) {
        List<SeatInventory> all = seats.findByTicketTypeId(ticketTypeId);
        if (all.isEmpty()) {
            throw new StockNotFoundException(ticketTypeId); // chưa seed / không phải SEATED
        }
        // SOLD đọc Postgres (nguồn sự thật); HELD = còn key giữ chỗ trong Redis (chưa commit/hết hạn).
        return all.stream().map(s -> {
            String status;
            if (s.isSold()) {
                status = SeatStatus.SOLD.name();
            } else if (Boolean.TRUE.equals(redis.hasKey(seatKey(s.getSeatId())))) {
                status = "HELD";
            } else {
                status = SeatStatus.AVAILABLE.name();
            }
            return new SeatAvailabilityResponse(s.getSeatId(), status);
        }).toList();
    }

    // ========================= HOLD ==========================================

    @Override
    public HoldResponse hold(HoldRequest request) {
        // Idempotency theo orderId: đã giữ chỗ cho đơn này và hold còn sống → trả lại.
        String existingHold = redis.opsForValue().get(orderKey(request.orderId()));
        if (existingHold != null) {
            String v = redis.opsForValue().get(holdKey(UUID.fromString(existingHold)));
            if (v != null) {
                HoldData d = HoldData.parse(v);
                return new HoldResponse(UUID.fromString(existingHold), d.ticketTypeId(), d.count());
            }
        }
        return request.seated() ? holdSeated(request) : holdGa(request);
    }

    private HoldResponse holdGa(HoldRequest request) {
        UUID ticketTypeId = request.ticketTypeId();
        if (request.quantity() == null || request.quantity() < 1) {
            throw new IllegalArgumentException("GA hold cần quantity >= 1");
        }
        int qty = request.quantity();
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
        redis.opsForValue().set(holdKey(holdId), HoldData.ga(ticketTypeId, request.orderId(), qty).serialize(), holdTtl);
        redis.opsForValue().set(orderKey(request.orderId()), holdId.toString(), holdTtl);
        log.info("Hold GA {} qty={} ticketType={} left={}", holdId, qty, ticketTypeId, left);
        return new HoldResponse(holdId, ticketTypeId, qty);
    }

    private HoldResponse holdSeated(HoldRequest request) {
        UUID ticketTypeId = request.ticketTypeId();
        List<UUID> seatIds = request.seatIds();

        // Ghế phải đã seed, đúng loại vé, và CHƯA bán (Postgres = nguồn sự thật cho SOLD).
        for (UUID seatId : seatIds) {
            SeatInventory seat = seats.findById(seatId)
                    .orElseThrow(() -> new StockNotFoundException(ticketTypeId));
            if (!seat.getTicketTypeId().equals(ticketTypeId) || seat.isSold()) {
                throw new InsufficientStockException(ticketTypeId);
            }
        }

        UUID holdId = UUID.randomUUID();
        // SET NX từng ghế (atomic) — ghế nào bị hold khác giữ → fail, nhả các ghế đã set.
        List<UUID> acquired = new ArrayList<>();
        for (UUID seatId : seatIds) {
            Boolean ok = redis.opsForValue().setIfAbsent(seatKey(seatId), holdId.toString(), holdTtl);
            if (Boolean.TRUE.equals(ok)) {
                acquired.add(seatId);
            } else {
                acquired.forEach(s -> redis.delete(seatKey(s))); // rollback các ghế đã giữ
                throw new InsufficientStockException(ticketTypeId);
            }
        }
        redis.opsForValue().set(holdKey(holdId),
                HoldData.seated(ticketTypeId, request.orderId(), seatIds).serialize(), holdTtl);
        redis.opsForValue().set(orderKey(request.orderId()), holdId.toString(), holdTtl);
        log.info("Hold SEATED {} seats={} ticketType={}", holdId, seatIds.size(), ticketTypeId);
        return new HoldResponse(holdId, ticketTypeId, seatIds.size());
    }

    // ========================= COMMIT ========================================

    @Override
    @Transactional
    public void commit(UUID holdId) {
        String v = redis.opsForValue().get(holdKey(holdId));
        if (v == null) {
            throw new HoldNotFoundException(holdId); // đã nhả/hết hạn/không tồn tại
        }
        HoldData d = HoldData.parse(v);
        if (d.seated()) {
            for (UUID seatId : d.seatIds()) {
                SeatInventory seat = seats.findById(seatId)
                        .orElseThrow(() -> new StockNotFoundException(d.ticketTypeId()));
                seat.markSold(d.orderId()); // idempotent: ghi SOLD + order
                redis.delete(seatKey(seatId));
            }
            log.info("Commit SEATED hold {} ticketType={} seats={}", holdId, d.ticketTypeId(), d.seatIds().size());
        } else {
            TicketStock stock = stocks.findById(d.ticketTypeId())
                    .orElseThrow(() -> new StockNotFoundException(d.ticketTypeId()));
            stock.commitSold(d.qty()); // counter Redis đã trừ từ lúc HOLD
            log.info("Commit GA hold {} ticketType={} qty={} sold={}",
                    holdId, d.ticketTypeId(), d.qty(), stock.getSoldQty());
        }
        redis.delete(holdKey(holdId)); // chốt rồi → xoá, chống release nhầm
    }

    // ========================= RELEASE =======================================

    @Override
    public void release(UUID holdId) {
        String v = redis.opsForValue().get(holdKey(holdId));
        if (v == null) {
            return; // idempotent: đã nhả hoặc đã commit
        }
        HoldData d = HoldData.parse(v);
        if (d.seated()) {
            // Chỉ nhả ghế CÒN thuộc hold này (tránh xoá nhầm hold khác đã giữ lại sau TTL).
            for (UUID seatId : d.seatIds()) {
                String owner = redis.opsForValue().get(seatKey(seatId));
                if (holdId.toString().equals(owner)) {
                    redis.delete(seatKey(seatId));
                }
            }
            log.info("Release SEATED hold {} ticketType={} seats={}", holdId, d.ticketTypeId(), d.seatIds().size());
        } else {
            redis.opsForValue().increment(gaKey(d.ticketTypeId()), d.qty()); // trả lại available
            log.info("Release GA hold {} ticketType={} qty={}", holdId, d.ticketTypeId(), d.qty());
        }
        redis.delete(holdKey(holdId));
    }

    /**
     * Nội dung hold lưu trong Redis. GA: {@code "GA|<ttId>|<orderId>|<qty>"};
     * SEATED: {@code "SEATED|<ttId>|<orderId>|<seatId,seatId,...>"}.
     */
    private record HoldData(boolean seated, UUID ticketTypeId, UUID orderId, int qty, List<UUID> seatIds) {

        static HoldData ga(UUID ttId, UUID orderId, int qty) {
            return new HoldData(false, ttId, orderId, qty, List.of());
        }

        static HoldData seated(UUID ttId, UUID orderId, List<UUID> seatIds) {
            return new HoldData(true, ttId, orderId, 0, seatIds);
        }

        /** Số đơn vị giữ chỗ (GA: qty; SEATED: số ghế). */
        int count() {
            return seated ? seatIds.size() : qty;
        }

        String serialize() {
            String data = seated
                    ? seatIds.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse("")
                    : String.valueOf(qty);
            return (seated ? "SEATED" : "GA") + "|" + ticketTypeId + "|" + orderId + "|" + data;
        }

        static HoldData parse(String v) {
            String[] p = v.split("\\|", 4);
            UUID ttId = UUID.fromString(p[1]);
            UUID orderId = UUID.fromString(p[2]);
            if ("SEATED".equals(p[0])) {
                List<UUID> seatIds = new ArrayList<>();
                if (!p[3].isEmpty()) {
                    for (String s : p[3].split(",")) {
                        seatIds.add(UUID.fromString(s));
                    }
                }
                return new HoldData(true, ttId, orderId, 0, seatIds);
            }
            return new HoldData(false, ttId, orderId, Integer.parseInt(p[3]), List.of());
        }
    }
}
