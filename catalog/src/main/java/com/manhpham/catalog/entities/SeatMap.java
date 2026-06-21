package com.manhpham.catalog.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Một ghế trong bản đồ ghế của loại vé SEATED — chỉ MÔ TẢ (tĩnh). Trạng thái bán từng ghế
 * thuộc Inventory (seat_inventory). {@code id} = seatId dùng xuyên suốt (Inventory/Order/Ticket).
 */
@Entity
@Table(name = "seat_map")
@Getter
public class SeatMap {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(nullable = false, length = 64)
    private String section;

    @Column(name = "row_label", nullable = false, length = 16)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false, length = 16)
    private String seatNumber;

    protected SeatMap() {
        // for JPA
    }

    public static SeatMap create(UUID ticketTypeId, String section, String rowLabel, String seatNumber) {
        SeatMap s = new SeatMap();
        s.id = UUID.randomUUID();
        s.ticketTypeId = ticketTypeId;
        s.section = section;
        s.rowLabel = rowLabel;
        s.seatNumber = seatNumber;
        return s;
    }

    /** Nhãn in lên vé, vd "A-12" (row-number). */
    public String label() {
        return rowLabel + "-" + seatNumber;
    }
}
