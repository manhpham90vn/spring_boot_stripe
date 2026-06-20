package com.manhpham.order.utils.exception;

import java.util.UUID;

/** Đơn không tồn tại HOẶC không thuộc về người đang gọi (trả 404 để không lộ tồn tại — chống IDOR). */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID id) {
        super("Order not found: " + id);
    }
}
