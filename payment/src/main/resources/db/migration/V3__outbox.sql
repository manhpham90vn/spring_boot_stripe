-- Transactional outbox cho Payment: phát PaymentSettled (đã thu/thất bại) → Kafka payment.events.
-- Debezium đọc WAL → app không tự publish. Cùng khuôn với auth/order (xem outbox-debezium.md).
CREATE TABLE outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_outbox_created_at ON outbox (created_at);
