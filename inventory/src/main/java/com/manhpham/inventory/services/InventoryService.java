package com.manhpham.inventory.services;

import com.manhpham.inventory.dto.HoldRequest;
import com.manhpham.inventory.dto.HoldResponse;
import com.manhpham.inventory.dto.SeatAvailabilityResponse;

import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ tồn kho (GA + SEATED). Ba thao tác khớp Saga (xem saga-purchase-flow.md):
 * HOLD (giữ chỗ) → COMMIT (chốt SOLD) → RELEASE (bù trừ/hết hạn).
 */
public interface InventoryService {

    /** GA: khởi tạo/đặt lại tồn cho một loại vé + nạp counter Redis. */
    void seed(UUID ticketTypeId, UUID eventId, int totalQty);

    /** SEATED: dựng seat_inventory cho danh sách ghế (status AVAILABLE). Idempotent. */
    void seedSeats(UUID ticketTypeId, UUID eventId, List<UUID> seatIds);

    /** Số còn lại (GA: counter Redis; SEATED: số ghế AVAILABLE ở Postgres). */
    int available(UUID ticketTypeId);

    /** SEATED: trạng thái từng ghế (AVAILABLE/HELD/SOLD) cho FE tô màu sơ đồ ghế. */
    List<SeatAvailabilityResponse> seatStatuses(UUID ticketTypeId);

    /** Giữ chỗ (GA: trừ counter nguyên tử; SEATED: SET NX từng ghế); idempotent theo orderId. */
    HoldResponse hold(HoldRequest request);

    /** Chốt SOLD: ghi bền xuống PostgreSQL, hold không còn nhả được. */
    void commit(UUID holdId);

    /** Nhả chỗ: trả lại tồn (bù trừ). Idempotent (hold đã nhả → no-op). */
    void release(UUID holdId);
}
