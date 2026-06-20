package com.manhpham.inventory.scheduler;

import com.manhpham.inventory.entities.TicketStock;
import com.manhpham.inventory.repositories.jpa.TicketStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưới an toàn: dựng lại counter Redis từ PostgreSQL (nguồn sự thật) khi counter BỊ THIẾU —
 * điển hình là Redis restart/mất dữ liệu. Counter = {@code total - sold}. Xem inventory-no-oversell.md §6.
 *
 * <p>GIỚI HẠN (walking skeleton): chỉ khôi phục khi counter VẮNG MẶT, dùng {@code setIfAbsent}
 * để KHÔNG đè counter đang chạy. Reconciliation ĐẦY ĐỦ còn phải tính các HOLD đang sống
 * (cũng mất khi Redis sự cố) → counter đúng = total − sold − held; phần đó phức tạp hơn,
 * bổ sung khi cần.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockReconciliationJob {

    private final TicketStockRepository stocks;
    private final StringRedisTemplate redis;

    @Scheduled(fixedDelayString = "${inventory.reconcile.interval:PT5M}")
    @Transactional(readOnly = true)
    public void reconcile() {
        for (TicketStock s : stocks.findAll()) {
            String key = "inv:ga:" + s.getTicketTypeId(); // khớp prefix ở InventoryServiceImpl
            if (Boolean.FALSE.equals(redis.hasKey(key))) {
                // setIfAbsent: chỉ nạp khi thật sự vắng, tránh ghi đè counter đang chạy.
                redis.opsForValue().setIfAbsent(key, String.valueOf(s.available()));
                log.warn("Reconcile: dựng lại counter {} = {} (total={} sold={})",
                        s.getTicketTypeId(), s.available(), s.getTotalQty(), s.getSoldQty());
            }
        }
    }
}
