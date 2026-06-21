-- SEATED (ghế ngồi): trạng thái bán bền của TỪNG ghế (nguồn sự thật). Redis (inv:seat:{id}
-- SET NX) chỉ giữ chỗ tạm; SOLD ghi xuống đây. seat_id = Catalog.seat_map.id.
CREATE TABLE seat_inventory (
    seat_id        UUID        PRIMARY KEY,
    ticket_type_id UUID        NOT NULL,
    event_id       UUID        NOT NULL,
    status         VARCHAR(16) NOT NULL,            -- AVAILABLE | SOLD
    order_id       UUID,                            -- đơn đã chốt ghế (khi SOLD)
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_seat_inventory_ticket_type ON seat_inventory (ticket_type_id);
CREATE INDEX ix_seat_inventory_event ON seat_inventory (event_id);
