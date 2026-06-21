package com.manhpham.catalog.entities;

/**
 * Loại tồn của vé: GA (vé đứng, đếm số lượng) vs SEATED (ghế ngồi, từng ghế cụ thể qua
 * seat_map). Quyết định cách Inventory seed & giữ chỗ (counter vs SET NX từng ghế).
 */
public enum TicketTypeKind {
    GA,
    SEATED
}
