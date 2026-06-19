-- Catalog = "có gì để bán": địa điểm, sự kiện, loại vé + giá.
-- KHÔNG giữ số lượng tồn (đó là Inventory). Đọc nhiều, ghi ít.
-- Toàn bộ schema đặt trong V1 (sở thích dev hiện tại).

CREATE TABLE venues (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    address    VARCHAR(500),
    city       VARCHAR(120),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE events (
    id             UUID         PRIMARY KEY,
    venue_id       UUID         NOT NULL REFERENCES venues (id),
    title          VARCHAR(300) NOT NULL,
    description    TEXT,
    -- DRAFT | ON_SALE | CLOSED | CANCELLED. Catalog chỉ giữ trạng thái hiển thị/bán,
    -- KHÔNG suy ra SOLD_OUT (đó là việc của Inventory dựa trên tồn kho thực).
    status         VARCHAR(32)  NOT NULL,
    starts_at      TIMESTAMPTZ  NOT NULL,
    sales_start_at TIMESTAMPTZ,
    sales_end_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_events_venue ON events (venue_id);
CREATE INDEX ix_events_status ON events (status);

CREATE TABLE ticket_types (
    id            UUID         PRIMARY KEY,
    event_id      UUID         NOT NULL REFERENCES events (id),
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(500),
    -- Giá lưu theo ĐƠN VỊ NHỎ NHẤT (minor units) + mã tiền tệ ISO-4217.
    -- Với JPY (zero-decimal) minor unit chính là số tiền — tránh bug nhân/chia ×100.
    price_minor   BIGINT       NOT NULL CHECK (price_minor >= 0),
    currency      VARCHAR(3)   NOT NULL,
    -- Giới hạn số vé loại này mỗi đơn (chống gom hàng); tồn kho thực ở Inventory.
    max_per_order INTEGER      NOT NULL DEFAULT 10 CHECK (max_per_order > 0),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_ticket_types_event ON ticket_types (event_id);
