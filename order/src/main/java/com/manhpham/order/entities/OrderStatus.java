package com.manhpham.order.entities;

/**
 * Trạng thái saga của đơn (event-driven). Xem state machine ở saga-purchase-flow.md §3.
 * Bản đầy đủ có thể thêm COMPLETED (đã phát vé), CANCELLED.
 */
public enum OrderStatus {
    PENDING,           // vừa tạo
    AWAITING_PAYMENT,  // đã giữ chỗ + đã khởi tạo thu tiền, CHỜ kết quả (đồng bộ thẻ / bất đồng bộ Konbini)
    PAID,              // đã thu tiền + chốt SOLD (thành công)
    REJECTED,          // không giữ được chỗ (hết vé)
    PAYMENT_FAILED     // thu tiền thất bại → đã bù trừ (nhả chỗ)
}
