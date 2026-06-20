package com.manhpham.inventory.utils.exception;

import java.util.UUID;

/** Không đủ vé để giữ chỗ (đã hết / không đủ số lượng yêu cầu). → HTTP 409. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(UUID ticketTypeId) {
        super("Insufficient stock for ticketType: " + ticketTypeId);
    }
}
