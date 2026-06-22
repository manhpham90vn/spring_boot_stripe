package com.manhpham.waitingroom.dto;

/**
 * Kết quả xếp hàng: {@code token} định danh chỗ trong hàng (client poll {@code /status} bằng nó),
 * {@code position} vị trí hiện tại (1-based), {@code etaSeconds} ước lượng thời gian chờ = vị trí / rate.
 */
public record EnqueueResponse(String token, long position, long etaSeconds) {
}
