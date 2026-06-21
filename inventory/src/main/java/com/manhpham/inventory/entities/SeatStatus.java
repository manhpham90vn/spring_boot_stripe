package com.manhpham.inventory.entities;

/** Trạng thái bán bền của một ghế (nguồn sự thật Postgres). Giữ chỗ tạm nằm ở Redis. */
public enum SeatStatus {
    AVAILABLE,
    SOLD
}
