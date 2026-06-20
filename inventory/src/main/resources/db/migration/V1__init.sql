-- Inventory = "còn bao nhiêu, ghế nào trống". Walking skeleton: CHỈ GA (vé đứng).
-- Redis giữ counter nóng (available); PostgreSQL là NGUỒN SỰ THẬT cho tổng & đã bán (SOLD).
CREATE TABLE ticket_stock (
    ticket_type_id UUID        PRIMARY KEY,          -- = TicketType.id ở Catalog
    event_id       UUID        NOT NULL,
    total_qty      INTEGER     NOT NULL CHECK (total_qty >= 0),
    sold_qty       INTEGER     NOT NULL DEFAULT 0 CHECK (sold_qty >= 0),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sold_le_total CHECK (sold_qty <= total_qty)
);

CREATE INDEX ix_ticket_stock_event ON ticket_stock (event_id);
