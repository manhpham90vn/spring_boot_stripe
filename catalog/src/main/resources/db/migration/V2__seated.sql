-- SEATED (ghế ngồi): phân biệt loại vé GA vs SEATED + bản đồ ghế (tĩnh, chỉ mô tả).
-- Trạng thái bán từng ghế KHÔNG ở đây — thuộc Inventory (seat_inventory).
ALTER TABLE ticket_types ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'GA';

CREATE TABLE seat_map (
    id             UUID        PRIMARY KEY,
    ticket_type_id UUID        NOT NULL REFERENCES ticket_types (id),
    section        VARCHAR(64) NOT NULL,
    row_label      VARCHAR(16) NOT NULL,
    seat_number    VARCHAR(16) NOT NULL,
    UNIQUE (ticket_type_id, section, row_label, seat_number)
);

CREATE INDEX ix_seat_map_ticket_type ON seat_map (ticket_type_id);
