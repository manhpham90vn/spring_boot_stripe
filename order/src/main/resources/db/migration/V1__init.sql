-- Order = trái tim điều phối (Saga orchestrator). Sở hữu đơn + trạng thái saga + outbox.
CREATE TABLE orders (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL,              -- chủ đơn (từ JWT sub) — dùng cho IDOR check
    event_id       UUID         NOT NULL,
    ticket_type_id UUID         NOT NULL,
    quantity       INTEGER      NOT NULL CHECK (quantity >= 1),
    amount_minor   BIGINT       NOT NULL CHECK (amount_minor >= 0),
    currency       VARCHAR(3)   NOT NULL,
    status         VARCHAR(32)  NOT NULL,              -- PENDING|HELD|PAID|REJECTED|PAYMENT_FAILED
    hold_id        UUID,                               -- trạng thái saga: id giữ chỗ ở Inventory
    payment_id     UUID,                               -- id payment ở Payment service
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_orders_user ON orders (user_id);

-- Transactional outbox: phát OrderCompleted khi đơn xong (Debezium đọc WAL → Kafka).
-- Xem outbox-debezium.md. Cùng khuôn với auth.
CREATE TABLE outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_outbox_created_at ON outbox (created_at);
