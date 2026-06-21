package com.manhpham.order.entities;

/**
 * Trạng thái saga của đơn (event-driven). Xem state machine ở saga-purchase-flow.md §3 (chuẩn).
 * COMPLETED (đã phát vé) là trạng thái cuối thiết kế — chuyển từ PAID khi Ticket xác nhận phát vé;
 * hiện CHƯA wire feedback Ticket→Order nên slice này dừng ở PAID (đã phát OrderCompleted).
 */
public enum OrderStatus {
    PENDING,           // vừa tạo
    AWAITING_PAYMENT,  // đã giữ chỗ + đã khởi tạo thu tiền, CHỜ kết quả (đồng bộ thẻ / bất đồng bộ Konbini)
    PAID,              // đã thu tiền + chốt SOLD (thành công)
    REJECTED,          // không giữ được chỗ (hết vé)
    PAYMENT_FAILED,    // thu tiền thất bại TRƯỚC khi thu được tiền → đã bù trừ (nhả chỗ)
    CANCELLED          // đã thu tiền NHƯNG hết vé lúc chốt SOLD → đã bù trừ (refund + nhả chỗ), saga §4
}
