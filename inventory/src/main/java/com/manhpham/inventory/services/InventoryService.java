package com.manhpham.inventory.services;

import com.manhpham.inventory.dto.HoldRequest;
import com.manhpham.inventory.dto.HoldResponse;

import java.util.UUID;

/**
 * Nghiệp vụ tồn kho GA. Ba thao tác khớp Saga (xem saga-purchase-flow.md):
 * HOLD (giữ chỗ) → COMMIT (chốt SOLD) → RELEASE (bù trừ/hết hạn).
 */
public interface InventoryService {

    /** Khởi tạo/đặt lại tồn cho một loại vé + nạp counter Redis. */
    void seed(UUID ticketTypeId, UUID eventId, int totalQty);

    /** Số còn lại (counter Redis). */
    int available(UUID ticketTypeId);

    /** Giữ chỗ: trừ counter nguyên tử; idempotent theo orderId. */
    HoldResponse hold(HoldRequest request);

    /** Chốt SOLD: ghi bền xuống PostgreSQL, hold không còn nhả được. */
    void commit(UUID holdId);

    /** Nhả chỗ: trả lại counter (bù trừ). Idempotent (hold đã nhả → no-op). */
    void release(UUID holdId);
}
