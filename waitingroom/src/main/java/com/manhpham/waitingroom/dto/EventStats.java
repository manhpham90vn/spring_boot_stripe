package com.manhpham.waitingroom.dto;

/**
 * Thống kê hàng chờ của 1 event cho trang admin (đọc trực tiếp từ Redis — store CHÍNH của
 * waitingroom). Tất cả số liệu là ảnh chụp tại thời điểm gọi.
 *
 * <ul>
 *   <li>{@code waiting} — số người đang xếp hàng ({@code ZCARD wr:queue}).</li>
 *   <li>{@code admittedTotal} — TỔNG người đã được thả vào (cộng dồn, không trừ khi PASS hết hạn).</li>
 *   <li>{@code ratePerSecond} — nhịp thả hiệu lực (per-event override hoặc config động toàn cục).</li>
 *   <li>{@code etaSeconds} — thời gian dự kiến rút cạn hàng = ceil(waiting / rate).</li>
 *   <li>{@code soldOut} — đã đánh dấu hết vé → admission ngừng thả.</li>
 * </ul>
 */
public record EventStats(
        String eventId,
        long waiting,
        long admittedTotal,
        int ratePerSecond,
        long etaSeconds,
        boolean soldOut) {
}
