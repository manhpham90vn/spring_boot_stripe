package com.manhpham.inventory.utils.exception;

import java.util.UUID;

/** Loại vé chưa được seed tồn kho. → HTTP 404. */
public class StockNotFoundException extends RuntimeException {
    public StockNotFoundException(UUID ticketTypeId) {
        super("Stock not found for ticketType: " + ticketTypeId);
    }
}
