package com.manhpham.waitingroom.dto;

/**
 * Cấu hình admission ĐỘNG (chỉnh từ trang admin lúc chạy, không cần redeploy). NGUỒN DUY NHẤT
 * lúc chạy là Redis ({@code wr:config}) — store CHÍNH của waitingroom (ràng buộc: KHÔNG PostgreSQL).
 * {@code application.properties} chỉ seed lần đầu (HSETNX), sau đó đọc thẳng từ Redis.
 *
 * <ul>
 *   <li>{@code rate} — nhịp thả mỗi giây (mặc định toàn cục; per-event vẫn override qua {@code wr:rate:{eventId}}).</li>
 *   <li>{@code tokenTtlSeconds} — hạn 1 chỗ trong hàng ({@code wr:token:*}).</li>
 *   <li>{@code admitTtlSeconds} — hạn PASS sau khi được thả ({@code wr:admit:*}).</li>
 * </ul>
 */
public record AdmissionConfig(int rate, long tokenTtlSeconds, long admitTtlSeconds) {
}
