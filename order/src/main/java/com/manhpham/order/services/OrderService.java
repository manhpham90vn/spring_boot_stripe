package com.manhpham.order.services;

import com.manhpham.order.dto.OrderResponse;
import com.manhpham.order.dto.PlaceOrderRequest;
import com.manhpham.common.core.event.PaymentSettledEvent;

import java.util.UUID;

public interface OrderService {

    /**
     * Đặt mua: tạo đơn → giữ chỗ → KHỞI TẠO thu tiền → đơn về {@code AWAITING_PAYMENT}.
     * Saga TIẾP TỤC bất đồng bộ khi nhận {@link #onPaymentSettled} (qua payment.events).
     * {@code email} (từ claim JWT) đính vào OrderCompleted để Notification gửi xác nhận.
     */
    OrderResponse place(UUID userId, String email, PlaceOrderRequest request);

    /**
     * Tiếp tục saga khi Payment báo kết quả: SUCCEEDED → chốt SOLD + PAID + phát vé;
     * FAILED → nhả chỗ + PAYMENT_FAILED. Idempotent (chỉ tác động khi đơn còn AWAITING_PAYMENT).
     */
    void onPaymentSettled(PaymentSettledEvent event);

    /** Xem đơn của chính mình (IDOR: chỉ chủ đơn). */
    OrderResponse get(UUID userId, UUID orderId);
}
