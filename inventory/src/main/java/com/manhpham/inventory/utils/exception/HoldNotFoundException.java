package com.manhpham.inventory.utils.exception;

import java.util.UUID;

/** Hold không tồn tại / đã hết hạn / đã commit (không commit lại được). → HTTP 409. */
public class HoldNotFoundException extends RuntimeException {
    public HoldNotFoundException(UUID holdId) {
        super("Hold not found or no longer active: " + holdId);
    }
}
